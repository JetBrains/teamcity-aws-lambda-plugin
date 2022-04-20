package jetbrains.buildServer.runner.lambda.aws

import com.amazonaws.auth.AWSCredentialsProvider
import jetbrains.buildServer.clouds.amazon.connector.featureDevelopment.AwsConnectionsManager
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.web.JsonControllerException
import jetbrains.buildServer.serverSide.SProject
import org.springframework.http.HttpStatus

class ServerAWSConnectionAwsClientFetcher(
        private val awsConnectionsManager: AwsConnectionsManager,
        private val properties: Map<String, String>,
        private val project: SProject) : AWSConnectionAwsClientFetcher() {
    private val awsConnectionBean by lazy {
        awsConnectionsManager.getLinkedAwsConnection(properties, project) ?: throw JsonControllerException("Not able to form aws connection", HttpStatus.BAD_REQUEST)
    }

    override fun getAWSRegion(): String = awsConnectionBean.region

    override fun buildCredentialsProvider(): AWSCredentialsProvider = awsConnectionBean.credentialsProvider

    override fun getLambdaEndpointUrl(): String? = properties[LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM]
}