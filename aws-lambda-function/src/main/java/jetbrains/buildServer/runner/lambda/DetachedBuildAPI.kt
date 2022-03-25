package jetbrains.buildServer.runner.lambda

import kotlinx.coroutines.Job

interface DetachedBuildApi {
    fun log(serviceMessage: String?)

    fun logWarning(message: String?)

    suspend fun finishBuild()

    fun failBuild(exception: Throwable, errorId: String? = null): Job
}