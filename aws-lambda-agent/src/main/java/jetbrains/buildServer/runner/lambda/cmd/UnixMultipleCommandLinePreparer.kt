package jetbrains.buildServer.runner.lambda.cmd

import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import java.io.File

class UnixMultipleCommandLinePreparer(private val context: BuildRunnerContext, private val logger: BuildProgressLogger) : UnixCommandLinePreparer(logger) {
    override fun writeBuildScriptContent(projectName: String, workingDirectory: File): List<String> {
        val filename = LambdaConstants.SCRIPT_CONTENT_FILENAME
        val scripts = context.runnerParameters.getValue(
                LambdaConstants.SCRIPT_CONTENT_PARAM
        ).split(LambdaConstants.SCRIPT_CONTENT_SPLITTER)

        return scripts.mapIndexed { index, value ->
            val indexedFilename = "$filename-$index"
            writeScript(workingDirectory, indexedFilename, value)
            indexedFilename
        }
    }
}