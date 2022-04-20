package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransfer

class LambdaFunctionResolverFactoryImpl(
        private val logger: Logger,
        private val awsLambda: AWSLambda,
        private val workingDirectoryTransfer: S3WorkingDirectoryTransfer,
        private val buildProperties: Map<String, String>
) : LambdaFunctionResolverFactory {
    private val lambdaMemory = buildProperties.getValue(LambdaConstants.MEMORY_SIZE_PARAM).toInt()
    private val lambdaStorage = buildProperties.getValue(LambdaConstants.STORAGE_SIZE_PARAM).toInt()
    private val iamRole = buildProperties.getValue(LambdaConstants.IAM_ROLE_PARAM)
    private val defaultImage = LambdaConstants.DEFAULT_LAMBDA_RUNTIME

    override fun getLambdaFunctionResolver(): LambdaFunctionResolver {
        val functionImage = buildProperties[LambdaConstants.ECR_IMAGE_URI_PARAM] ?: defaultImage
        val normalizedFunctionImageName = functionImage.substringAfter("/").substringBefore(":")
        val lambdaFunctionName = "${LambdaConstants.FUNCTION_NAME}-$normalizedFunctionImageName"

        return if (functionImage == defaultImage) {
            DefaultImageLambdaFunctionResolver(lambdaMemory, iamRole, lambdaStorage, functionImage, logger, awsLambda, lambdaFunctionName, workingDirectoryTransfer)
        } else {
            EcrImageLambdaFunctionResolver(lambdaMemory, iamRole, lambdaStorage, functionImage, logger, awsLambda, lambdaFunctionName, workingDirectoryTransfer)
        }
    }
}