package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.GetRoleRequest
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.util.CollectionsUtil
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.util.amazon.AWSCommonParams
import org.apache.commons.validator.routines.UrlValidator

class LambdaPropertiesProcessor(private val getIamClient: (Map<String, String>) -> AmazonIdentityManagement) :
    PropertiesProcessor {
    override fun process(properties: MutableMap<String, String>): MutableCollection<InvalidProperty> {
        val invalids = mutableMapOf<String, String>()

        invalids.putAll(AWSCommonParams.validate(properties, false))

        val awsValidationFailed = invalids.isNotEmpty()

        val endpointUrl = properties[LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM]
        if (StringUtil.isNotEmpty(endpointUrl) && !UrlValidator(UrlValidator.ALLOW_LOCAL_URLS).isValid(endpointUrl)) {
            invalids[LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM] = LambdaConstants.LAMBDA_ENDPOINT_URL_ERROR
        }

        if (StringUtil.isEmpty(properties[LambdaConstants.SCRIPT_CONTENT_PARAM])) {
            invalids[LambdaConstants.SCRIPT_CONTENT_PARAM] = LambdaConstants.SCRIPT_CONTENT_ERROR
        }

        when (properties[LambdaConstants.MEMORY_SIZE_PARAM]?.toIntOrNull()) {
            null -> invalids[LambdaConstants.MEMORY_SIZE_PARAM] = LambdaConstants.MEMORY_SIZE_VALUE_ERROR
            !in LambdaConstants.MIN_MEMORY_SIZE..LambdaConstants.MAX_MEMORY_SIZE -> invalids[LambdaConstants.MEMORY_SIZE_PARAM] =
                LambdaConstants.MEMORY_SIZE_VALUE_ERROR
        }

        when (properties[LambdaConstants.STORAGE_SIZE_PARAM]?.toIntOrNull()) {
            null -> invalids[LambdaConstants.STORAGE_SIZE_PARAM] = LambdaConstants.STORAGE_SIZE_ERROR
            !in LambdaConstants.MIN_STORAGE_SIZE..LambdaConstants.MAX_STORAGE_SIZE -> invalids[LambdaConstants.STORAGE_SIZE_PARAM] =
                LambdaConstants.STORAGE_SIZE_VALUE_ERROR
        }

        val iamRole = properties[LambdaConstants.IAM_ROLE_PARAM]
        when {
            iamRole == null -> invalids[LambdaConstants.IAM_ROLE_PARAM] = LambdaConstants.IAM_ROLE_ERROR
            iamRole == LambdaConstants.IAM_ROLE_SELECT_OPTION -> invalids[LambdaConstants.IAM_ROLE_PARAM] =
                LambdaConstants.IAM_ROLE_ERROR
            !awsValidationFailed && !isValidIam(properties, iamRole) -> invalids[LambdaConstants.IAM_ROLE_PARAM] =
                LambdaConstants.IAM_ROLE_INVALID_ERROR
        }

        return CollectionsUtil.convertCollection(invalids.entries) { source ->
            InvalidProperty(source.key, source.value)
        }
    }

    private fun isValidIam(properties: MutableMap<String, String>, iamRole: String): Boolean {
        val iam = getIamClient(properties)

        val iamRoleName = getRoleName(iamRole)
        val roleRequest = GetRoleRequest().apply {
            roleName = iamRoleName
        }

        return try {
            iam.getRole(roleRequest)
            true
        } catch (e: NoSuchEntityException) {
            false
        }
    }

    private fun getRoleName(iamRole: String): String {
        val roleArnPrefix = ":role/"

        return iamRole.substring(iamRole.indexOf(roleArnPrefix) + roleArnPrefix.length)
    }
}