package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.*
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants

class LambdaFunctionResolverImpl(
    private val context: BuildRunnerContext,
    private val awsLambda: AWSLambda,
    private val functionDownloader: FunctionDownloader
) :
    LambdaFunctionResolver {
    private val memorySize = context.runnerParameters.getValue(LambdaConstants.MEMORY_SIZE_PARAM).toInt()
    private val iamRole = context.runnerParameters.getValue(LambdaConstants.IAM_ROLE_PARAM)
    private val defaultImage = LambdaConstants.DEFAULT_LAMBDA_RUNTIME

    override fun resolveFunction(): String {
        val functionImage = context.runnerParameters[LambdaConstants.ECR_IMAGE_URI_PARAM] ?: defaultImage
        val normalizedFunctionImageName = functionImage
            .substringAfter("/")
            .substringBefore(":")
        val lambdaFunctionName = "${LambdaConstants.FUNCTION_NAME}-$normalizedFunctionImageName"

        val getFunctionRequest = GetFunctionRequest().apply {
            functionName = lambdaFunctionName
        }

        try {
            //TODO: Check Function Details: TW-75372
            awsLambda.getFunction(getFunctionRequest)
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
        val createFunctionRequest = if (functionImageUri == defaultImage) {
            CreateFunctionRequest().apply {
                functionName = lambdaFunctionName
                code = FunctionCode().apply {
                    zipFile = functionDownloader.downloadFunctionCode()
                    handler = LambdaConstants.FUNCTION_HANDLER
                }
                role = iamRole
                publish = true
                packageType = "Zip"
                runtime = LambdaConstants.DEFAULT_LAMBDA_RUNTIME
                memorySize = this@LambdaFunctionResolverImpl.memorySize
                timeout = LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT
            }
        } else {
            CreateFunctionRequest().apply {
                functionName = lambdaFunctionName
                code = FunctionCode().apply {
                    imageUri = functionImageUri
                }
                role = iamRole
                publish = true
                packageType = "Image"
                memorySize = this@LambdaFunctionResolverImpl.memorySize
                timeout = LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT
            }
        }

        awsLambda.createFunction(createFunctionRequest)
        awaitFunctionCreation(lambdaFunctionName)
    }

    private fun awaitFunctionCreation(lambdaFunctionName: String) {
        val invokeRequest = InvokeRequest().apply {
            functionName = lambdaFunctionName
        }

        for (i in 1..MAX_TRIES) {
            try {
                awsLambda.invoke(invokeRequest)
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

    companion object {
        const val MAX_TRIES = 10
        const val WAIT_TIME = 10000L
    }
}