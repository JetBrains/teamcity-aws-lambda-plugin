package jetbrains.buildServer.runner.lambda.model

data class RunDetails(
    val username: String,
    val password: String,
    val buildId: String,
    val teamcityServerUrl: String,
    val customScriptFilename: String,
    val directoryId: String,
    val runNumber: Int
)