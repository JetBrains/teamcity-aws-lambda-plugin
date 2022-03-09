package jetbrains.buildServer.runner.lambda.build

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.intellij.execution.configurations.GeneralCommandLine
import jetbrains.buildServer.runner.lambda.DetachedBuildApi
import jetbrains.buildServer.runner.lambda.RunDetails
import kotlinx.coroutines.Deferred
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

class LambdaCommandLine internal constructor(private val generalCommandLine: GeneralCommandLine, private val logger: LambdaLogger) {

    fun executeCommandLine(detachedBuildApi: DetachedBuildApi): List<Deferred<Any?>> {
        val process = generalCommandLine.createProcess()
        val stream = process.inputStream
        val buffer = ByteArray(8192)
        val logJobs = mutableListOf<Deferred<Any?>>()
        var size = stream.read(buffer)
        while (size != -1){
            logJobs.add(detachedBuildApi.logAsync(buffer.decodeToString(0, size)))
            size = stream.read(buffer)
        }

        logger.log("Process finished with exit code ${process.waitFor()}\n")

        return logJobs.toList()
    }

    companion object {
        operator fun invoke(runDetails: RunDetails, logger: LambdaLogger): LambdaCommandLine {
            val generalCommandLine = GeneralCommandLine()

            val filename = createExecutableFile(runDetails)
            generalCommandLine.apply {
                //TODO: Change this to recognize a project's working directory: TW-75269
                exePath = "/usr/bin/sh"
                setWorkingDirectory(filename.parentFile)
                addParameter(filename.name)
                envParams = runDetails.envParams
            }

            return LambdaCommandLine(generalCommandLine, logger)
        }

        private fun createExecutableFile(runDetails: RunDetails): File {
            val executableFile = kotlin.io.path.createTempFile("executable.sh").toFile()

            val writer = BufferedWriter(FileWriter(executableFile))
            writer.write(runDetails.customScript)
            writer.close()

            return executableFile
        }
    }
}