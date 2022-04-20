package jetbrains.buildServer.runner.lambda.aws

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.s3.transfer.TransferManager

interface AwsClientFetcher {
    fun getAWSLambdaClient(): AWSLambda

    fun getTransferManager(): TransferManager
}