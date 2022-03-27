package jetbrains.buildServer.runner.lambda.web

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder
import com.amazonaws.services.identitymanagement.model.*
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.model.IamRole
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.auth.AccessChecker
import jetbrains.buildServer.util.amazon.AWSCommonParams
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.http.HttpStatus
import javax.servlet.http.HttpServletRequest

class LambaDefaultIamRoleCreateController(
    descriptor: PluginDescriptor,
    controllerManager: WebControllerManager,
    projectManager: ProjectManager,
    accessManager: AccessChecker,
) : JsonController<IamRole>(
    descriptor, controllerManager, projectManager, accessManager, LambdaConstants.IAM_ROLES_CREATE_PATH, setOf(
        METHOD_POST
    )
) {
    override fun handle(request: HttpServletRequest, properties: Map<String, String>): IamRole {
        try {
            val iam = AWSCommonParams.withAWSClients<AmazonIdentityManagement, Exception>(properties) { clients ->
                AmazonIdentityManagementClientBuilder.standard()
                    .withClientConfiguration(clients.clientConfiguration)
                    .withCredentials(AWSCommonParams.getCredentialsProvider(properties))
                    .build()
            }

            return getRole(iam) ?: createRole(iam)
        }catch (e: AmazonIdentityManagementException){
            throw JsonControllerException(e.errorMessage, HttpStatus.valueOf(e.statusCode))
        }

    }

    private fun createRole(iam: AmazonIdentityManagement): IamRole {
        val policyResource = javaClass.classLoader.getResource(ROLE_POLICY_DOCUMENT)
            ?: throw JsonControllerException(
                "Failed to find policy resource document",
                HttpStatus.INTERNAL_SERVER_ERROR
            )

        val policyDocument = policyResource.readText()
        val createRoleRequest = CreateRoleRequest().apply {
            roleName = LambdaConstants.DEFAULT_LAMBDA_ARN_NAME
            assumeRolePolicyDocument = policyDocument
        }
        val createRoleResult = iam.createRole(createRoleRequest)

        val attachRolePolicyRequest = AttachRolePolicyRequest().apply {
            roleName = LambdaConstants.DEFAULT_LAMBDA_ARN_NAME
            policyArn = LambdaConstants.AWS_LAMBDA_BASIC_EXECUTION_ROLE_POLICY
        }

        iam.attachRolePolicy(attachRolePolicyRequest)

        return IamRole(createRoleResult.role.arn, createRoleResult.role.roleName)
    }

    private fun getRole(iam: AmazonIdentityManagement): IamRole? {
        val getRoleRequest = GetRoleRequest().apply {
            roleName = LambdaConstants.DEFAULT_LAMBDA_ARN_NAME
        }

        return try {
            val getRoleResult = iam.getRole(getRoleRequest)
            IamRole(getRoleResult.role.arn, getRoleResult.role.roleName)
        } catch (e: NoSuchEntityException) {
            null
        }
    }

    companion object {
        const val ROLE_POLICY_DOCUMENT = "aws-role-policy.json"
    }
}