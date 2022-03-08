package jetbrains.buildServer.runner.lambda

import kotlinx.coroutines.Job

interface DetachedBuildApi {
    fun log(serviceMessage: String): Job

    suspend fun finishBuild()
}