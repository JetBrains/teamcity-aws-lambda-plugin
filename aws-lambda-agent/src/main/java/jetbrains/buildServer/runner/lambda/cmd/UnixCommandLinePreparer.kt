package jetbrains.buildServer.runner.lambda.cmd

import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.runner.lambda.LambdaConstants
import java.io.File

abstract class UnixCommandLinePreparer(private val logger: BuildProgressLogger): CommandLinePreparer {
    protected fun writeScript(workingDirectory: File, filename: String, scriptContent: String?) {
        val scriptContentFile = File("${workingDirectory.absolutePath}/$filename")

        if (!scriptContentFile.exists()) {
            logger.message("Script file $scriptContentFile not found, creating new one")
            scriptContentFile.createNewFile()
        }

        val writer = scriptContentFile.printWriter()
        val script = LambdaConstants.SCRIPT_CONTENT_HEADER +
                LambdaConstants.SCRIPT_CONTENT_CHANGE_DIRECTORY_PREFIX + scriptContent

        logger.message("Writing script content to $scriptContentFile")
        writer.write(script)
        writer.close()
    }
}