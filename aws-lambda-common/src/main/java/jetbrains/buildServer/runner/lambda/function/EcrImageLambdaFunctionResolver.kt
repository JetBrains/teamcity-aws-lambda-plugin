package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.*
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.directory.WorkingDirectoryTransfer

class EcrImageLambdaFunctionResolver(
        lambdaMemory: Int,
        iamRole: String,
        lambdaStorage: Int,
        functionImage: String,
        logger: Logger,
        awsLambda: AWSLambda,
        lambdaFunctionName: String,
        private val workingDirectoryTransfer: WorkingDirectoryTransfer) : LambdaFunctionResolverEx(lambdaMemory, iamRole, lambdaStorage, functionImage, logger, awsLambda, lambdaFunctionName) {
    override fun getFunctionRequest(): CreateFunctionRequest {
        val storage = EphemeralStorage().apply {
            size = lambdaStorage
        }

        return CreateFunctionRequest().apply {
            functionName = lambdaFunctionName
            code = getFunctionCode()
            role = iamRole
            publish = true
            packageType = "Image"
            ephemeralStorage = storage
            memorySize = lambdaMemory
            timeout = LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT
        }
    }

    fun getFunctionCode(): FunctionCode = FunctionCode().apply {
        imageUri = functionImage
    }

    override fun updateFunction() {
        logger.message("Function $lambdaFunctionName's container image is outdated, updating it")
        val updateFunctionCodeRequest = UpdateFunctionCodeRequest().apply {
            functionName = lambdaFunctionName
            imageUri = functionImage
            publish = true
        }

        awsLambda.updateFunctionCode(updateFunctionCodeRequest)
        awaitFunctionStatus()
    }

    override fun doesFunctionNeedUpdate(function: GetFunctionResult): Boolean =
            function.code.imageUri != functionImage || functionImage.endsWith(LATEST_TAG)
        // Whenever the container image is the latest, we are unable to know if there have been updates to the image

    companion object {
        private const val LATEST_TAG = ":latest"
    }
}