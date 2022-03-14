package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.*
import com.amazonaws.services.identitymanagement.model.GetPolicyRequest
import com.amazonaws.services.lambda.AWSLambdaAsync
import com.amazonaws.services.lambda.model.*
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels

class LambdaFunctionResolverImpl(
    private val context: BuildRunnerContext,
    private val awsLambdaAsync: AWSLambdaAsync,
    private val iam: AmazonIdentityManagement
) :
    LambdaFunctionResolver {
    private val memorySize = context.runnerParameters.getValue(LambdaConstants.MEMORY_SIZE_PARAM).toInt()
    private val defaultImage = LambdaConstants.DEFAULT_LAMBDA_RUNTIME

    override fun resolveFunction(): String {
        val functionImage = context.runnerParameters[LambdaConstants.ECR_IMAGE_URI_PARAM] ?: defaultImage
        val lambdaFunctionName = "${LambdaConstants.FUNCTION_NAME}-$functionImage"

        val getFunctionRequest = GetFunctionRequest().apply {
            functionName = lambdaFunctionName
        }

        try {
            //TODO: Check Function Details: TW-75372
            awsLambdaAsync.getFunction(getFunctionRequest)
        } catch (e: ResourceNotFoundException) {
            createFunction(functionImage, lambdaFunctionName)
        } catch (e: AWSLambdaException) {
            // Should the function be executed locally, a 404 will happen
            if (e.statusCode != 404) {
                throw e
            }
        }

        return lambdaFunctionName
    }

    private fun createFunction(functionImageUri: String, lambdaFunctionName: String) {
        // TODO: Add logic for function versioning: TW-75371
        val userRole = resolveRoleArn()
        val createFunctionRequest = if (functionImageUri == defaultImage) {
            CreateFunctionRequest().apply {
                functionName = lambdaFunctionName
                code = FunctionCode().apply {
                    zipFile = downloadFunctionCode()
                    handler = LambdaConstants.FUNCTION_HANDLER
                }
                role = userRole
                publish = true
                packageType = "Zip"
                runtime = LambdaConstants.DEFAULT_LAMBDA_RUNTIME
                memorySize = this@LambdaFunctionResolverImpl.memorySize
                timeout = LambdaConstants.MAX_TIMEOUT
            }
        } else {
            CreateFunctionRequest().apply {
                functionName = lambdaFunctionName
                code = FunctionCode().apply {
                    imageUri = functionImageUri
                    handler = LambdaConstants.FUNCTION_HANDLER
                }
                role = userRole
                publish = true
                packageType = "Image"
                memorySize = this@LambdaFunctionResolverImpl.memorySize
                timeout = LambdaConstants.MAX_TIMEOUT
            }
        }

        awsLambdaAsync.createFunction(createFunctionRequest)
        awaitFunctionCreation(lambdaFunctionName)
    }

    private fun awaitFunctionCreation(lambdaFunctionName: String) {
        val invokeRequest = InvokeRequest().apply {
            functionName = lambdaFunctionName
        }

        for (i in 1..MAX_TRIES) {
            try {
                awsLambdaAsync.invoke(invokeRequest)
                break
            } catch (e: ResourceConflictException) {
                Thread.sleep(WAIT_TIME)
                if (i == MAX_TRIES) {
                    throw e
                }
                continue
            } catch (e: AmazonServiceException) {
                if (e.statusCode == 500) {
                    break
                } else {
                    throw e
                }
            }
        }
    }

    private fun resolveRoleArn(): String {
        val arn = iam.user.user.arn
        val accountId = arn.substring(LambdaConstants.IAM_SUFFIX.length + 2, arn.indexOf(":user"))
        val lambdaPolicyArn = "${LambdaConstants.IAM_SUFFIX}::$accountId:policy/${LambdaConstants.LAMBDA_ARN_NAME}"
        val lambdaRoleArn = "${LambdaConstants.IAM_SUFFIX}::$accountId:role/${LambdaConstants.LAMBDA_ARN_NAME}"


        if (!policyExists(lambdaPolicyArn)) {
            val createPolicyRequest = CreatePolicyRequest().apply {
                policyName = LambdaConstants.LAMBDA_ARN_NAME
                policyDocument = LambdaFunctionResolver.ARN_POLICY
            }

            iam.createPolicy(createPolicyRequest)
        }

        if (!roleExists()) {
            val createRoleRequest = CreateRoleRequest().apply {
                roleName = LambdaConstants.LAMBDA_ARN_NAME
                assumeRolePolicyDocument = LambdaFunctionResolver.ROLE_POLICY_DOCUMENT

            }

            iam.createRole(createRoleRequest)

            val attachPolicyRequest = AttachRolePolicyRequest().apply {
                roleName = LambdaConstants.LAMBDA_ARN_NAME
                policyArn = lambdaPolicyArn
            }

            iam.attachRolePolicy(attachPolicyRequest)
        }

        return lambdaRoleArn
    }

    private fun roleExists(): Boolean = try {
        val getRoleRequest = GetRoleRequest().apply {
            roleName = LambdaConstants.LAMBDA_ARN_NAME
        }

        iam.getRole(getRoleRequest)
        true
    } catch (e: NoSuchEntityException) {
        false
    }

    private fun policyExists(policyArn: String): Boolean =
        try {
            val getPolicyRequest = GetPolicyRequest().apply {
                setPolicyArn(policyArn)
            }
            iam.getPolicy(getPolicyRequest)
            true
        } catch (e: NoSuchEntityException) {
            false
        }


    private fun downloadFunctionCode(): ByteBuffer {
        val readableByteChannel = Channels.newChannel(URL(LambdaConstants.S3_CODE_FUNCTION_URL).openStream())
        val tempFile = kotlin.io.path.createTempFile().toFile()
        val tempFileChannel = tempFile.outputStream().channel

        tempFileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE)

        return ByteBuffer.wrap(tempFile.readBytes())
    }

    companion object {
        const val MAX_TRIES = 10
        const val WAIT_TIME = 10000L
    }
}