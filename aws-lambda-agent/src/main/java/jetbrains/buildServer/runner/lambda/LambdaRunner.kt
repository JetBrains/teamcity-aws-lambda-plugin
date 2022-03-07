package jetbrains.buildServer.runner.lambda

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.lambda.AWSLambdaAsync
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.runner.lambda.LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM
import jetbrains.buildServer.runner.lambda.LambdaConstants.RUNNER_TYPE
import jetbrains.buildServer.util.amazon.AWSCommonParams.getCredentialsProvider
import jetbrains.buildServer.util.amazon.AWSCommonParams.withAWSClients

class LambdaRunner : AgentBuildRunner {
    override fun createBuildProcess(runningBuild: AgentRunningBuild, context: BuildRunnerContext): BuildProcess =
        LambdaBuildProcess(context, getLambdaClient(context), jacksonObjectMapper())

    private fun getLambdaClient(context: BuildRunnerContext) =
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


    override fun getRunnerInfo(): AgentBuildRunnerInfo = object : AgentBuildRunnerInfo {
        override fun getType(): String = RUNNER_TYPE

        override fun canRun(agentConfiguration: BuildAgentConfiguration): Boolean = true
    }
}