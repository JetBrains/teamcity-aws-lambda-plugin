package jetbrains.buildServer.runner.lambda

data class RunDetails(
    val username: String,
    val password: String,
    val buildId: String,
    val teamcityServerUrl: String
)