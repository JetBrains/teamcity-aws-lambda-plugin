package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.AWSLambdaAsync
import com.amazonaws.services.lambda.model.InvokeRequest
import com.fasterxml.jackson.databind.ObjectMapper
import jetbrains.buildServer.agent.BuildFinishedStatus
import jetbrains.buildServer.agent.BuildProcess
import jetbrains.buildServer.agent.BuildRunnerContext
import java.util.concurrent.atomic.AtomicBoolean

class LambdaBuildProcess(
    private val context: BuildRunnerContext,
    private val awsLambda: AWSLambdaAsync,
    private val objectMapper: ObjectMapper,
) :
    BuildProcess {

    private val myIsInterrupted: AtomicBoolean = AtomicBoolean()
    private val myIsFinished: AtomicBoolean = AtomicBoolean()

    private fun executeTask(): BuildFinishedStatus {
        val runDetails = getRunDetails()

        val invokeRequest = InvokeRequest()
            .withFunctionName(LambdaConstants.FUNCTION_NAME)
            .withPayload(objectMapper.writeValueAsString(runDetails))

        if (isInterrupted) {
            return BuildFinishedStatus.INTERRUPTED
        }

        awsLambda.invokeAsync(invokeRequest)
        myIsFinished.set(true)
        return BuildFinishedStatus.FINISHED_DETACHED
    }

    private fun getRunDetails(): RunDetails = RunDetails(
        username = context.buildParameters.allParameters.getValue(LambdaConstants.USERNAME_SYSTEM_PROPERTY),
        password = context.buildParameters.allParameters.getValue(LambdaConstants.PASSWORD_SYSTEM_PROPERTY),
        buildId = context.configParameters.getValue(LambdaConstants.TEAMCITY_BUILD_ID),
        teamcityServerUrl = context.configParameters.getValue(LambdaConstants.TEAMCITY_SERVER_URL)
            .replace("localhost", "172.17.0.1")
    )

    override fun start() {}

    override fun isInterrupted(): Boolean = myIsInterrupted.get()

    override fun isFinished(): Boolean = myIsFinished.get()

    override fun interrupt() {
        myIsInterrupted.set(true)
    }

    override fun waitFor(): BuildFinishedStatus = when {
        isFinished -> BuildFinishedStatus.FINISHED_DETACHED
        isInterrupted -> BuildFinishedStatus.INTERRUPTED
        else -> executeTask()
    }
}