package jetbrains.buildServer.runner.lambda.aws

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.AWSLambdaClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants

class AWSConnectionAwsClientFetcher(private val context: BuildRunnerContext) : AwsClientFetcher {
    private val credentialsProvider by lazy {
        val accessKey = context.runnerParameters.getValue(LambdaConstants.AWS_ACCESS_KEY_ID)
        val secretKey = context.runnerParameters.getValue(LambdaConstants.AWS_SECRET_ACCESS_KEY)
        AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey))
    }

    private val region = context.runnerParameters.getValue(LambdaConstants.AWS_REGION)


    override fun getAWSLambdaClient(): AWSLambda {
        val clientBuilder = AWSLambdaClientBuilder.standard()
            .withCredentials(credentialsProvider)

        if (context.runnerParameters.containsKey(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM)) {
            clientBuilder.withEndpointConfiguration(
                AwsClientBuilder.EndpointConfiguration(
                    context.runnerParameters[LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM],
                    region
                )
            )
        } else {
            clientBuilder.withRegion(region)
        }

        return clientBuilder.build()
    }

    override fun getTransferManager(): TransferManager =
        TransferManagerBuilder.standard()
            .withS3Client(createS3Client())
            .build()

    private fun createS3Client(): AmazonS3 = AmazonS3ClientBuilder.standard()
        .withCredentials(credentialsProvider)
        .withRegion(region)
        .build()
}