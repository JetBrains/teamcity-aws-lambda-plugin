package jetbrains.buildServer.runner.lambda.build

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.intellij.execution.configurations.GeneralCommandLine
import jetbrains.buildServer.runner.lambda.DetachedBuildApi
import jetbrains.buildServer.runner.lambda.RunDetails
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream

class LambdaCommandLine internal constructor(
    private val generalCommandLine: GeneralCommandLine,
    private val logger: LambdaLogger
) {

    suspend fun executeCommandLine(detachedBuildApi: DetachedBuildApi): MutableList<Deferred<Any?>> {
        val process = generalCommandLine.createProcess()
        val logJobs = mutableListOf<Deferred<Any?>>()
        logProcessOutput(process, logJobs, detachedBuildApi)


        logJobs.add(logProcessExitAsync(process, detachedBuildApi))
        logJobs.awaitAll()
        return logJobs
    }

    private suspend fun logProcessOutput(
        process: Process,
        logJobs: MutableList<Deferred<Any?>>,
        detachedBuildApi: DetachedBuildApi
    ) {
        val inputStreamJob = logOutputStream(process.inputStream, logJobs) {
            detachedBuildApi.logAsync(it)
        }
        val errorStreamJob = logOutputStream(process.errorStream, logJobs) {
            detachedBuildApi.logWarningAsync(it)
        }

        inputStreamJob.join()
        errorStreamJob.join()
    }

    private fun logOutputStream(
        stream: InputStream,
        logJobs: MutableList<Deferred<Any?>>,
        logCall: (String) -> Deferred<Any?>
    ) = CoroutineScope(Dispatchers.IO).launch {
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            var streamSize = stream.read(buffer)

            while (streamSize != -1) {
                logJobs.add(logCall(buffer.decodeToString(0, streamSize)))
                streamSize = stream.read(buffer)
            }
        }
    }

    private fun logProcessExitAsync(process: Process, detachedBuildApi: DetachedBuildApi): Deferred<Any?> {

        val exitCode = process.waitFor()
        val exitMessage = "Process finished with exit code $exitCode\n"
        logger.log(exitMessage)


        return if (exitCode != 0) {
            detachedBuildApi.failBuildAsync(ProcessFailedException(exitMessage))
        } else {
            detachedBuildApi.logAsync(exitMessage)
        }
    }

    companion object {
        private const val JAVA_HOME = "JAVA_HOME"

        operator fun invoke(runDetails: RunDetails, logger: LambdaLogger, workingDirectory: File): LambdaCommandLine {
            val generalCommandLine = createCommandLine(workingDirectory, runDetails)

            return LambdaCommandLine(generalCommandLine, logger)
        }

        internal fun createCommandLine(
            workingDirectory: File,
            runDetails: RunDetails
        ): GeneralCommandLine {
            val generalCommandLine = GeneralCommandLine()
            val mergedEnvParams = mergeEnvParams(runDetails)

            generalCommandLine.apply {
                exePath = "/bin/sh"
                setWorkingDirectory(workingDirectory)
                addParameter("${workingDirectory.absolutePath}/${runDetails.customScriptFilename}")
                envParams = mergedEnvParams
            }
            return generalCommandLine
        }

        private fun mergeEnvParams(runDetails: RunDetails): Map<String, String> {
            val envParams = runDetails.envParams.toMutableMap()

            envParams.putAll(System.getenv())

            //Need to remove some the JAVA_HOME in order to not interfere with java programs
            envParams.remove(JAVA_HOME)
            return envParams
        }
    }
}