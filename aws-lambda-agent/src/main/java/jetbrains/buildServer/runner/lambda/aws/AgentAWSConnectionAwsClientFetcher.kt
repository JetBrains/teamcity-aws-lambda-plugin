package jetbrains.buildServer.runner.lambda.aws

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import jetbrains.buildServer.runner.lambda.LambdaConstants

class AgentAWSConnectionAwsClientFetcher(private val buildProperties: Map<String, String>): AWSConnectionAwsClientFetcher() {
    override fun getAWSRegion(): String = buildProperties.getValue(LambdaConstants.AWS_REGION)

    override fun buildCredentialsProvider(): AWSCredentialsProvider {
        val accessKey = buildProperties.getValue(LambdaConstants.AWS_ACCESS_KEY_ID)
        val secretKey = buildProperties.getValue(LambdaConstants.AWS_SECRET_ACCESS_KEY)
        return AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey))
    }

    override fun getLambdaEndpointUrl(): String? = buildProperties[LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM]
}