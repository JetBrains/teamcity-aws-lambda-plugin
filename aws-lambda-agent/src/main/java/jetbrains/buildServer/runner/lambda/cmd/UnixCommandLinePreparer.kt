package jetbrains.buildServer.runner.lambda.cmd

import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import java.io.File

class UnixCommandLinePreparer(private val context: BuildRunnerContext): CommandLinePreparer {
    override fun writeBuildScriptContent(projectName: String, workingDirectory: File): String {
        val filename = "$projectName-${LambdaConstants.SCRIPT_CONTENT_FILENAME}"
        val scriptContentFile = File("${workingDirectory.absolutePath}/$filename")

        if (!scriptContentFile.exists()) {
            scriptContentFile.createNewFile()
        }

        val writer = scriptContentFile.printWriter()
        val scriptContent =
            LambdaConstants.SCRIPT_CONTENT_CHANGE_DIRECTORY_PREFIX + context.runnerParameters.getValue(LambdaConstants.SCRIPT_CONTENT_PARAM)

        writer.write(scriptContent)
        writer.close()

        return filename
    }
}