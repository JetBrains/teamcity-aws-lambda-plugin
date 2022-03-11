package jetbrains.buildServer.runner.lambda.cmd

import java.io.File

interface CommandLinePreparer {
    fun writeBuildScriptContent(projectName: String, workingDirectory: File): String
}