package jetbrains.buildServer.runner.lambda

import RunDetails
import com.amazonaws.services.lambda.AWSLambdaAsync
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder
import com.amazonaws.services.lambda.model.InvokeRequest
import com.amazonaws.services.lambda.model.InvokeResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.util.amazon.AWSCommonParams.getCredentialsProvider
import jetbrains.buildServer.util.amazon.AWSCommonParams.withAWSClients
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class LambdaRunner : AgentBuildRunner {
    override fun createBuildProcess(runningBuild: AgentRunningBuild, context: BuildRunnerContext): BuildProcess =
        object : BuildProcess {
            private lateinit var invokeResultFuture: Future<InvokeResult>

            private val awsLambda by lazy {
                getLambdaClient()
            }
            private val objectMapper = jacksonObjectMapper()
            private val myIsInterrupted = AtomicBoolean()
            private val myIsFinished = AtomicBoolean()


            fun executeTask(): BuildFinishedStatus {
                val runDetails = RunDetails(context.runnerParameters)

                val invokeRequest = InvokeRequest()
                    .withFunctionName(FUNCTION_NAME)
                    .withPayload(objectMapper.writeValueAsString(runDetails))

                if (isInterrupted) {
                    invokeResultFuture = awsLambda.invokeAsync(invokeRequest)
                    return BuildFinishedStatus.INTERRUPTED
                }

                myIsFinished.set(true)
                return BuildFinishedStatus.FINISHED_DETACHED
            }

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

            private fun getLambdaClient() =
                withAWSClients<AWSLambdaAsync, Exception>(context.runnerParameters) { clients ->
                    AWSLambdaAsyncClientBuilder.standard()
                        .withClientConfiguration(clients.clientConfiguration)
                        .withCredentials(getCredentialsProvider(context.runnerParameters))
                        .withRegion(clients.region)
                        .build()
                }
        }

    override fun getRunnerInfo(): AgentBuildRunnerInfo = object : AgentBuildRunnerInfo {
        override fun getType(): String = RUNNER_TYPE

        override fun canRun(agentConfiguration: BuildAgentConfiguration): Boolean = true

    }

    companion object {
        private const val RUNNER_TYPE = "aws.lambda"
        private const val FUNCTION_NAME = "TeamcityLambdaRunner"

    }
}