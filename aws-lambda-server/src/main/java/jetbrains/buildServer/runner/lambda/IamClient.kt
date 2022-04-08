package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder
import jetbrains.buildServer.clouds.amazon.connector.AwsConnectorFactory
import jetbrains.buildServer.clouds.amazon.connector.utils.parameters.AwsCloudConnectorConstants
import jetbrains.buildServer.runner.lambda.web.JsonControllerException
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager
import org.springframework.http.HttpStatus

object IamClient {
    fun getIamClientFromProperties(oAuthConnectionsManager: OAuthConnectionsManager, awsConnectorFactory: AwsConnectorFactory, project: SProject, properties: Map<String, String>): AmazonIdentityManagement {
        val connectionId = properties[AwsCloudConnectorConstants.CHOSEN_AWS_CONN_ID_PARAM] ?: throw JsonControllerException("No connection ID found", HttpStatus.BAD_REQUEST)

        val connection = oAuthConnectionsManager.findConnectionById(project, connectionId)
                ?: throw JsonControllerException("No connection $connectionId found", HttpStatus.BAD_REQUEST)

        val credentialsProvider = awsConnectorFactory.buildAwsCredentialsProvider(connection.parameters)

        return AmazonIdentityManagementClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .build()
    }
}