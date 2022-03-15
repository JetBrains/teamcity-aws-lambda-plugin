package jetbrains.buildServer.runner.lambda.build

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.intellij.execution.configurations.GeneralCommandLine
import jetbrains.buildServer.runner.lambda.DetachedBuildApi
import jetbrains.buildServer.runner.lambda.RunDetails
import kotlinx.coroutines.Deferred
import java.io.File

class LambdaCommandLine internal constructor(
    private val generalCommandLine: GeneralCommandLine,
    private val logger: LambdaLogger
) {

    fun executeCommandLine(detachedBuildApi: DetachedBuildApi): List<Deferred<Any?>> {
        val process = generalCommandLine.createProcess()
        val logJobs = mutableListOf<Deferred<Any?>>()
        logProcessOutput(process, logJobs, detachedBuildApi)


        logJobs.add(logProcessExitAsync(process, detachedBuildApi))
        return logJobs.toList()
    }

    private fun logProcessOutput(
        process: Process,
        logJobs: MutableList<Deferred<Any?>>,
        detachedBuildApi: DetachedBuildApi
    ) {
        val inputStream = process.inputStream
        val inputStreamBuffer = ByteArray(8192)
        var inputStreamSize = inputStream.read(inputStreamBuffer)
        val errorStream = process.errorStream
        val errorStreamBuffer = ByteArray(8192)
        var errorStreamSize = errorStream.read(errorStreamBuffer)

        while (inputStreamSize != -1 || errorStreamSize != -1) {
            if (inputStreamSize != -1) {
                logJobs.add(detachedBuildApi.logAsync(inputStreamBuffer.decodeToString(0, inputStreamSize)))
                inputStreamSize = inputStream.read(inputStreamBuffer)
            }

            if (errorStreamSize != -1) {
                logJobs.add(detachedBuildApi.logWarningAsync(errorStreamBuffer.decodeToString(0, errorStreamSize)))
                errorStreamSize = errorStream.read(errorStreamBuffer)
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
                exePath = "/usr/bin/sh"
                setWorkingDirectory(workingDirectory)
                addParameter("${workingDirectory.absolutePath}/${runDetails.directoryId}/${runDetails.customScriptFilename}")
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