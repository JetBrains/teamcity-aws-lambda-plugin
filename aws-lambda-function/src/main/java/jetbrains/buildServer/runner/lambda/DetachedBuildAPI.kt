package jetbrains.buildServer.runner.lambda

interface DetachedBuildApi {
    fun log(serviceMessage: String)

    suspend fun finishBuild()
}