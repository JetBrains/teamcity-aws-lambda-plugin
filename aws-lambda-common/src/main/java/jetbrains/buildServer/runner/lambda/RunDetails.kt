package jetbrains.buildServer.runner.lambda

data class RunDetails(
    val username: String,
    val password: String,
    val buildId: String,
    val teamcityServerUrl: String,
    val customScriptFilename: String, // This can be highly inefficient, especially if the script is large. This should be passed as a file, when that's supported: TW-75269
    val directoryId: String
)