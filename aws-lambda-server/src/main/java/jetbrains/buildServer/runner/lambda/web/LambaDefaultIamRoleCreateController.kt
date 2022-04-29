package jetbrains.buildServer.runner.lambda.web

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.*
import jetbrains.buildServer.clouds.amazon.connector.AwsConnectorFactory
import jetbrains.buildServer.clouds.amazon.connector.featureDevelopment.AwsConnectionsManager
import jetbrains.buildServer.controllers.AuthorizationInterceptor
import jetbrains.buildServer.runner.lambda.IamClient
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.model.IamRole
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.SecurityContextEx
import jetbrains.buildServer.serverSide.auth.AccessChecker
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.http.HttpStatus
import javax.servlet.http.HttpServletRequest

class LambaDefaultIamRoleCreateController(
        descriptor: PluginDescriptor,
        controllerManager: WebControllerManager,
        projectManager: ProjectManager,
        accessManager: AccessChecker,
        authInterceptor: AuthorizationInterceptor,
        private val awsConnectionsManager: AwsConnectionsManager
) : JsonController<IamRole>(
        descriptor, controllerManager, authInterceptor, projectManager, accessManager, LambdaConstants.IAM_ROLES_CREATE_PATH, setOf(
        METHOD_POST
)
) {
    override fun handle(project: SProject, request: HttpServletRequest, properties: Map<String, String>): IamRole {
        try {
            val iam = IamClient.getIamClientFromProperties(awsConnectionsManager, project, properties)
            return getRole(iam) ?: createRole(iam)
        } catch (e: AmazonIdentityManagementException) {
            throw JsonControllerException(e.errorMessage, HttpStatus.valueOf(e.statusCode))
        }

    }

    override fun checkPermissions(securityContext: SecurityContextEx, request: HttpServletRequest) {
        val project = getProject(request)
        securityContext.accessChecker.checkCanEditProject(project)
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