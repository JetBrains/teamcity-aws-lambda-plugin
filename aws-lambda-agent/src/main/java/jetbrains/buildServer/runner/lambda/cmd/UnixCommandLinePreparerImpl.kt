package jetbrains.buildServer.runner.lambda.cmd

import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import java.io.File

open class UnixCommandLinePreparerImpl(private val context: BuildRunnerContext, private val logger: BuildProgressLogger) :
     UnixCommandLinePreparer(logger) {
    override fun writeBuildScriptContent(projectName: String, workingDirectory: File): List<String> {
        val filename = LambdaConstants.SCRIPT_CONTENT_FILENAME
        writeScript(workingDirectory, filename, context.runnerParameters.getValue(
                LambdaConstants.SCRIPT_CONTENT_PARAM
        ))

        return listOf(filename)
    }
}