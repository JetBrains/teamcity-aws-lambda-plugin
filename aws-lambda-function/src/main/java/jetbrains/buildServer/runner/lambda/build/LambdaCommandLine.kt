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

    suspend fun executeCommandLine(detachedBuildApi: DetachedBuildApi) {
        val process = generalCommandLine.createProcess()
        logProcessOutput(process, detachedBuildApi)


        logProcessExitAsync(process, detachedBuildApi)?.join()
    }

    private suspend fun logProcessOutput(
        process: Process,
        detachedBuildApi: DetachedBuildApi
    ) {
        val inputStreamJob = logOutputStream(process.inputStream) {
            detachedBuildApi.log(it)
        }
        val errorStreamJob = logOutputStream(process.errorStream) {
            detachedBuildApi.logWarning(it)
        }

        inputStreamJob.join()
        errorStreamJob.join()
    }

    private fun logOutputStream(
        stream: InputStream,
        logCall: (String) -> Unit
    ) = CoroutineScope(Dispatchers.IO).launch {
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            var streamSize = stream.read(buffer)

            while (streamSize != -1) {
                logCall(buffer.decodeToString(0, streamSize))
                streamSize = stream.read(buffer)
            }
        }
    }

    private fun logProcessExitAsync(process: Process, detachedBuildApi: DetachedBuildApi): Job? {

        val exitCode = process.waitFor()
        val exitMessage = "Process finished with exit code $exitCode\n"
        logger.log(exitMessage)


        return if (exitCode != 0) {
            detachedBuildApi.failBuild(ProcessFailedException(exitMessage))
        } else {
            detachedBuildApi.log(exitMessage)
            null
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