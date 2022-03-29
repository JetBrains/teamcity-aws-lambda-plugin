package jetbrains.buildServer.runner.lambda

import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.serverSide.BuildPromotionEx
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.agentless.DetachedBuildStatusProvider
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class LambdaDeploymentStatusProvider : DetachedBuildStatusProvider {
    private val buildsBeginningTime: MutableMap<Long, Instant> = mutableMapOf()

    override fun getDescription(): String = LambdaConstants.RUNNER_DISPLAY_NAME

    override fun accepts(build: SRunningBuild, trackingInfo: String?): Boolean {
        val runnerType =
            (build.buildPromotion as BuildPromotionEx)
                .buildSettings
                .buildRunners
                .find { it.runType.type == LambdaConstants.RUNNER_TYPE }

        val isLambdaBuildStep = runnerType != null
        if (isLambdaBuildStep) {
            buildsBeginningTime.computeIfAbsent(build.buildId) { Instant.now() }
        }
        return isLambdaBuildStep
    }

    override fun updateBuild(build: SRunningBuild, trackingInfo: String?) {
        val currentTime = Instant.now()
        if (hasFunctionTimedOut(build, currentTime)) {
            build.addBuildProblem(
                BuildProblemData.createBuildProblem(
                    ID,
                    LambdaConstants.TIMEOUT_BUILD_PROBLEM_TYPE,
                    "Timeout of ${LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT} seconds has been exceeded"
                )
            )

            build.finish(Date.from(currentTime))
        }
    }

    private fun hasFunctionTimedOut(
        build: SRunningBuild,
        currentTime: Instant
    ): Boolean {
        val beginningTime = buildsBeginningTime[build.buildId]

        return if (beginningTime != null) {
            build.isDetachedFromAgent &&
                    (ChronoUnit.SECONDS.between(
                        beginningTime,
                        currentTime
                    ) >= LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT + LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT_LEEWAY)
        } else {
            buildsBeginningTime[build.buildId] = Instant.now()
            false
        }
    }

    companion object {
        private const val ID = "BUILD_TIMEOUT"
    }
}