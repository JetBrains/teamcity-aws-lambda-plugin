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
        val stream = process.inputStream
        val buffer = ByteArray(8192)
        val logJobs = mutableListOf<Deferred<Any?>>()
        var size = stream.read(buffer)
        while (size != -1) {
            logJobs.add(detachedBuildApi.logAsync(buffer.decodeToString(0, size)))
            size = stream.read(buffer)
        }

        logger.log("Process finished with exit code ${process.waitFor()}\n")

        return logJobs.toList()
    }

    companion object {
        operator fun invoke(runDetails: RunDetails, logger: LambdaLogger, workingDirectory: File): LambdaCommandLine {
            val generalCommandLine = createCommandLine(workingDirectory, runDetails)

            return LambdaCommandLine(generalCommandLine, logger)
        }

        internal fun createCommandLine(
            workingDirectory: File,
            runDetails: RunDetails
        ): GeneralCommandLine {
            val generalCommandLine = GeneralCommandLine()

            generalCommandLine.apply {
                exePath = "/usr/bin/sh"
                setWorkingDirectory(workingDirectory)
                addParameter("${workingDirectory.absolutePath}/${runDetails.directoryId}/${runDetails.customScriptFilename}")
                envParams = runDetails.envParams
            }
            return generalCommandLine
        }
    }
}