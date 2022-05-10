package jetbrains.buildServer.runner.lambda.model

data class RunDetails(
        val username: String,
        val password: String,
        val teamcityServerUrl: String,
        val customScriptFilename: String,
        val directoryId: String,
        val invocationId: Int,
        val buildDetails: BuildDetails
)

data class BuildDetails(
        val buildId: String,
        val buildTypeId: String,
        val agentName: String
)
