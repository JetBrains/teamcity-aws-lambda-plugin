package jetbrains.buildServer.runner.lambda

import kotlinx.coroutines.Deferred

interface DetachedBuildApi {
    fun logAsync(serviceMessage: String?): Deferred<Any?>

    suspend fun finishBuild()
}