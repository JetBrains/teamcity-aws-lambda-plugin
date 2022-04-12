package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.*
import com.amazonaws.waiters.WaiterParameters
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransfer
import java.io.FileNotFoundException

class LambdaFunctionResolverImpl(
    private val context: BuildRunnerContext,
    private val logger: BuildProgressLogger,
    private val awsLambda: AWSLambda,
    private val workingDirectoryTransfer: S3WorkingDirectoryTransfer
) : LambdaFunctionResolver {
    private val lambdaMemory = context.runnerParameters.getValue(LambdaConstants.MEMORY_SIZE_PARAM).toInt()
    private val iamRole = context.runnerParameters.getValue(LambdaConstants.IAM_ROLE_PARAM)
    private val defaultImage = LambdaConstants.DEFAULT_LAMBDA_RUNTIME

    override fun resolveFunction(): String {
        val functionImage = context.runnerParameters[LambdaConstants.ECR_IMAGE_URI_PARAM] ?: defaultImage
        val normalizedFunctionImageName = functionImage.substringAfter("/").substringBefore(":")
        val lambdaFunctionName = "${LambdaConstants.FUNCTION_NAME}-$normalizedFunctionImageName"

        val getFunctionRequest = GetFunctionRequest().apply {
            functionName = lambdaFunctionName
        }

        try {
            logger.message("Checking if function $lambdaFunctionName already exists...")
            val function = awsLambda.getFunction(getFunctionRequest)

            logger.message("Function $lambdaFunctionName already exists, checking if configuration is updated...")
            if (doesFunctionConfigurationNeedUpdate(function)) {
                updateFunctionConfiguration(lambdaFunctionName)
            }

            if (functionImage != defaultImage && doesFunctionContainerNeedUpdate(function, functionImage)) {
                updateFunctionContainer(functionImage, lambdaFunctionName)
            } else if (functionImage == defaultImage && doesFunctionCodeNeedUpdate(lambdaFunctionName)) {
                uploadFunctionCode(lambdaFunctionName)
            }
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

    private fun doesFunctionCodeNeedUpdate(lambdaFunctionName: String): Boolean {
        val hash = getHash()

        val props = workingDirectoryTransfer.getValueProps(lambdaFunctionName)

        return props?.getUserMetaDataOf(CHECKSUM_KEY) != hash
    }

    private fun getHash(): String =
        (javaClass.classLoader.getResource(FUNCTION_JAR_HASH)?.readText()
            ?: throw FileNotFoundException("Function jar $FUNCTION_JAR_HASH not found"))


    private fun updateFunctionContainer(functionImage: String, lambdaFunctionName: String) {
        logger.message("Function $lambdaFunctionName's container image is outdated, updating it")
        val updateFunctionCodeRequest = UpdateFunctionCodeRequest().apply {
            functionName = lambdaFunctionName
            imageUri = functionImage
            publish = true
        }

        awsLambda.updateFunctionCode(updateFunctionCodeRequest)
        awaitFunctionStatus(lambdaFunctionName)
    }

    private fun doesFunctionContainerNeedUpdate(function: GetFunctionResult, functionImage: String): Boolean =
        function.code.imageUri != functionImage || functionImage.endsWith(LATEST_TAG)
    // Whenever the container image is the latest, we are unable to know if there have been updates to the image

    private fun updateFunctionConfiguration(
        lambdaFunctionName: String
    ) {
        logger.message("Function $lambdaFunctionName's configuration is outdated, updating it")
        val updateFunctionConfigurationRequest = UpdateFunctionConfigurationRequest().apply {
            functionName = lambdaFunctionName
            role = iamRole
            memorySize = lambdaMemory
        }

        awsLambda.updateFunctionConfiguration(updateFunctionConfigurationRequest)
        awaitFunctionStatus(lambdaFunctionName)
    }

    private fun doesFunctionConfigurationNeedUpdate(
        function: GetFunctionResult
    ): Boolean {
        if (function.configuration.memorySize != lambdaMemory) {
            return true
        }
        if (function.configuration.role != iamRole) {
            return true
        }
        return false
    }


    private fun createFunction(functionImageUri: String, lambdaFunctionName: String) {
        logger.message("Function $lambdaFunctionName does not exist, creating it...")
        val createFunctionRequest = if (functionImageUri == defaultImage) {
            uploadFunctionCode(lambdaFunctionName)
            CreateFunctionRequest().apply {
                functionName = lambdaFunctionName
                code = FunctionCode().apply {
                    s3Bucket = workingDirectoryTransfer.bucketName
                    s3Key = lambdaFunctionName
                    handler = LambdaConstants.FUNCTION_HANDLER
                }
                role = iamRole
                publish = true
                packageType = "Zip"
                runtime = LambdaConstants.DEFAULT_LAMBDA_RUNTIME
                memorySize = this@LambdaFunctionResolverImpl.lambdaMemory
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
                memorySize = this@LambdaFunctionResolverImpl.lambdaMemory
                timeout = LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT
            }
        }

        awsLambda.createFunction(createFunctionRequest)
        awaitFunctionStatus(lambdaFunctionName)
        logger.message("Function $lambdaFunctionName has been created successfully")
    }

    private fun uploadFunctionCode(lambdaFunctionName: String) {
        logger.message("Updating function's code...")
        val functionCodeResource = getFunctionCode()

        val hash = getHash()

        val functionCode = kotlin.io.path.createTempFile().toFile()
        functionCode.writeBytes(functionCodeResource)

        val props = mapOf(Pair(CHECKSUM_KEY, hash))
        workingDirectoryTransfer.upload(lambdaFunctionName, functionCode, props)
        logger.message("Updated function's code successfully")
    }

    private fun getFunctionCode() = (javaClass.classLoader.getResource(FUNCTION_JAR)?.readBytes()
        ?: throw FileNotFoundException("Function jar $FUNCTION_JAR not found"))

    private fun awaitFunctionStatus(lambdaFunctionName: String) {
        val waiters = awsLambda.waiters()
        waiters.functionActiveV2().run(
            WaiterParameters(
                GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
        )

        waiters.functionUpdatedV2().run(
            WaiterParameters(
                GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
        )
    }

    companion object {
        private const val LATEST_TAG = ":latest"
        private const val FUNCTION_JAR = "aws-lambda-function-all.jar"
        internal const val FUNCTION_JAR_HASH = "aws-lambda-function.jar.sha512"
        internal const val CHECKSUM_KEY = "checksum"
    }
}