package jetbrains.buildServer.runner.lambda

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.lambda.AWSLambdaAsync
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder
import com.amazonaws.services.lambda.model.InvokeRequest
import com.amazonaws.services.lambda.model.InvokeResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.runner.lambda.LambdaConstants.FUNCTION_NAME
import jetbrains.buildServer.runner.lambda.LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM
import jetbrains.buildServer.runner.lambda.LambdaConstants.PASSWORD_SYSTEM_PROPERTY
import jetbrains.buildServer.runner.lambda.LambdaConstants.RUNNER_TYPE
import jetbrains.buildServer.runner.lambda.LambdaConstants.TEAMCITY_BUILD_ID
import jetbrains.buildServer.runner.lambda.LambdaConstants.TEAMCITY_SERVER_URL
import jetbrains.buildServer.runner.lambda.LambdaConstants.USERNAME_SYSTEM_PROPERTY
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
                val runDetails = getRunDetails()

                val invokeRequest = InvokeRequest()
                    .withFunctionName(FUNCTION_NAME)
                    .withPayload(objectMapper.writeValueAsString(runDetails))

                if (isInterrupted) {
                    return BuildFinishedStatus.INTERRUPTED
                }

                invokeResultFuture = awsLambda.invokeAsync(invokeRequest)
                myIsFinished.set(true)
                return BuildFinishedStatus.FINISHED_DETACHED
            }

            private fun getRunDetails(): RunDetails = RunDetails(
                username = context.buildParameters.allParameters.getValue(USERNAME_SYSTEM_PROPERTY),
                password = context.buildParameters.allParameters.getValue(PASSWORD_SYSTEM_PROPERTY),
                buildId = context.configParameters.getValue(TEAMCITY_BUILD_ID),
                teamcityServerUrl = context.configParameters.getValue(TEAMCITY_SERVER_URL)
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

            private fun getLambdaClient() =
                withAWSClients<AWSLambdaAsync, Exception>(context.runnerParameters) { clients ->
                    val clientBuilder = AWSLambdaAsyncClientBuilder.standard()
                        .withClientConfiguration(clients.clientConfiguration)
                        .withCredentials(getCredentialsProvider(context.runnerParameters))

                    if (context.runnerParameters.containsKey(LAMBDA_ENDPOINT_URL_PARAM)) {
                        clientBuilder.withEndpointConfiguration(
                            AwsClientBuilder.EndpointConfiguration(
                                context.runnerParameters[LAMBDA_ENDPOINT_URL_PARAM],
                                clients.region
                            )
                        )
                    } else {
                        clientBuilder.withRegion(clients.region)
                    }

                    clientBuilder.build()
                }
        }

    override fun getRunnerInfo(): AgentBuildRunnerInfo = object : AgentBuildRunnerInfo {
        override fun getType(): String = RUNNER_TYPE

        override fun canRun(agentConfiguration: BuildAgentConfiguration): Boolean = true

    }

    companion object {

    }
}