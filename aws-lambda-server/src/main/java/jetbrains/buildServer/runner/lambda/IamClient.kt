package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder
import jetbrains.buildServer.clouds.amazon.connector.errors.features.LinkedAwsConnNotFoundException
import jetbrains.buildServer.clouds.amazon.connector.featureDevelopment.AwsConnectionsManager
import jetbrains.buildServer.runner.lambda.web.JsonControllerException
import jetbrains.buildServer.serverSide.SProject
import org.springframework.http.HttpStatus

object IamClient {
    fun getIamClientFromProperties(awsConnectionsManager: AwsConnectionsManager, project: SProject, properties: Map<String, String>): AmazonIdentityManagement {
        try {
            val credentialsProvider = awsConnectionsManager.getLinkedAwsConnection(properties, project)?.credentialsProvider
                    ?: throw JsonControllerException("No AWS Connection found", HttpStatus.BAD_REQUEST)

            return AmazonIdentityManagementClientBuilder.standard()
                    .withCredentials(credentialsProvider)
                    .build()
        } catch (e: LinkedAwsConnNotFoundException) {
            throw JsonControllerException(e.localizedMessage, HttpStatus.BAD_REQUEST);
        }
    }
}