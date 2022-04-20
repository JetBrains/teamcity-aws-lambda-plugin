package jetbrains.buildServer.runner.lambda.aws

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.AWSLambdaClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import jetbrains.buildServer.runner.lambda.LambdaConstants

abstract class AWSConnectionAwsClientFetcher : AwsClientFetcher {
    private val credentialsProvider by lazy {
        buildCredentialsProvider()
    }

    private val region by lazy {
        getAWSRegion()
    }

    abstract fun getAWSRegion(): String

    abstract fun buildCredentialsProvider(): AWSCredentialsProvider

    abstract fun getLambdaEndpointUrl(): String?


    override fun getAWSLambdaClient(): AWSLambda {
        val clientBuilder = AWSLambdaClientBuilder.standard()
            .withCredentials(credentialsProvider)

        if (getLambdaEndpointUrl()?.isNotEmpty() == true) {
            clientBuilder.withEndpointConfiguration(
                AwsClientBuilder.EndpointConfiguration(
                    getLambdaEndpointUrl(),
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