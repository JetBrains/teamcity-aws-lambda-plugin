package jetbrains.buildServer.runner.lambda.web

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder
import com.amazonaws.services.identitymanagement.model.AmazonIdentityManagementException
import com.amazonaws.services.identitymanagement.model.ListRolesRequest
import com.amazonaws.services.identitymanagement.model.Role
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.model.IamRole
import jetbrains.buildServer.runner.lambda.model.IamRolesList
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.auth.AccessChecker
import jetbrains.buildServer.util.amazon.AWSCommonParams
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.http.HttpStatus
import javax.servlet.http.HttpServletRequest

class LambdaIamRolesListController(
    descriptor: PluginDescriptor,
    controllerManager: WebControllerManager,
    projectManager: ProjectManager,
    accessManager: AccessChecker,
) : JsonController<IamRolesList>(
    descriptor, controllerManager, projectManager, accessManager, LambdaConstants.IAM_ROLES_LIST_PATH, setOf(
        METHOD_POST
    )
) {
    override fun handle(request: HttpServletRequest, properties: Map<String, String>): IamRolesList {
        try {
            val iam = AWSCommonParams.withAWSClients<AmazonIdentityManagement, Exception>(properties) { clients ->
                AmazonIdentityManagementClientBuilder.standard()
                    .withClientConfiguration(clients.clientConfiguration)
                    .withCredentials(AWSCommonParams.getCredentialsProvider(properties))
                    .build()
            }

            val roles = getRoles(iam).map { IamRole(it.arn, it.roleName) }
            val defaultRole = roles.find { it.roleName.endsWith(LambdaConstants.LAMBDA_ARN_NAME) }

            return IamRolesList(roles, defaultRole)
        } catch (e: AmazonIdentityManagementException) {
            throw JsonControllerException(e.errorMessage, HttpStatus.valueOf(e.statusCode))
        }
    }

    private fun getRoles(iam: AmazonIdentityManagement): List<Role> {
        var rolesResult = iam.listRoles(ListRolesRequest())
        val roles =
            rolesResult.roles

        while (rolesResult.isTruncated()) {
            rolesResult = iam.listRoles(ListRolesRequest().apply { marker = rolesResult.marker })
            roles.addAll(rolesResult.roles)
        }

        return roles
    }
}