package jetbrains.buildServer.runner.lambda

import kotlinx.coroutines.Deferred

interface DetachedBuildApi {
    fun logAsync(serviceMessage: String?): Deferred<Any?>

    fun logWarningAsync(message: String?) : Deferred<Any?>

    suspend fun finishBuild()

    fun failBuildAsync(exception: Throwable, errorId: String? = null): Deferred<Any?>
}