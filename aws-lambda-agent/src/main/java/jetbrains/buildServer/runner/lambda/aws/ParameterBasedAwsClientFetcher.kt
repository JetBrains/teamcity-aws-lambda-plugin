package jetbrains.buildServer.runner.lambda.aws

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.AWSLambdaClientBuilder
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.util.amazon.AWSCommonParams
import jetbrains.buildServer.util.amazon.AWSCommonParams.withAWSClients

class ParameterBasedAwsClientFetcher(private val context: BuildRunnerContext) : AwsClientFetcher {
    override fun getAWSLambdaClient(): AWSLambda =
        withAWSClients<AWSLambda, Exception>(context.runnerParameters) { clients ->
            val clientBuilder = AWSLambdaClientBuilder.standard()
                .withClientConfiguration(clients.clientConfiguration)
                .withCredentials(AWSCommonParams.getCredentialsProvider(context.runnerParameters))

            if (context.runnerParameters.containsKey(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM)) {
                clientBuilder.withEndpointConfiguration(
                    AwsClientBuilder.EndpointConfiguration(
                        context.runnerParameters[LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM],
                        clients.region
                    )
                )
            } else {
                clientBuilder.withRegion(clients.region)
            }

            clientBuilder.build()
        }


    override fun getTransferManager(): TransferManager =
        withAWSClients<TransferManager, Exception>(context.runnerParameters) { clients ->
            TransferManagerBuilder.standard()
                .withS3Client(clients.createS3Client())
                .build()
        }
}