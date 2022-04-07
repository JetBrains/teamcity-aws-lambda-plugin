package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder
import jetbrains.buildServer.util.amazon.AWSClients
import jetbrains.buildServer.util.amazon.AWSCommonParams

object IamClient {
    fun AWSClients.createIamClient(properties: Map<String, String>): AmazonIdentityManagement =
        AmazonIdentityManagementClientBuilder.standard()
            .withClientConfiguration(clientConfiguration)
            .withCredentials(AWSCommonParams.getCredentialsProvider(properties))
            .build()

}