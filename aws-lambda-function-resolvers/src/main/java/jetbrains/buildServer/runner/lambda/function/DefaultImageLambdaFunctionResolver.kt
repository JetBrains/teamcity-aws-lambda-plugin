package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.*
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransfer
import java.io.FileNotFoundException

class DefaultImageLambdaFunctionResolver(
        lambdaMemory: Int,
        iamRole: String,
        lambdaStorage: Int,
        functionImage: String,
        logger: Logger,
        awsLambda: AWSLambda,
        lambdaFunctionName: String,
        private val workingDirectoryTransfer: S3WorkingDirectoryTransfer) :
        LambdaFunctionResolverEx(lambdaMemory, iamRole, lambdaStorage, functionImage, logger, awsLambda, lambdaFunctionName) {

    override fun getFunctionRequest(): CreateFunctionRequest {
        val storage = EphemeralStorage().apply {
            size = lambdaStorage
        }
        return CreateFunctionRequest().apply {
            functionName = lambdaFunctionName
            code = getFunctionCode()
            role = iamRole
            handler = LambdaConstants.FUNCTION_HANDLER
            publish = true
            packageType = "Zip"
            ephemeralStorage = storage
            runtime = LambdaConstants.DEFAULT_LAMBDA_RUNTIME
            memorySize = lambdaMemory
            timeout = LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT
        }
    }

    fun getFunctionCode(): FunctionCode {
        uploadFunctionCode()
        return FunctionCode().apply {
            s3Bucket = workingDirectoryTransfer.bucketName
            s3Key = lambdaFunctionName
        }
    }

    private fun getHash(): String =
            (javaClass.classLoader.getResource(FUNCTION_JAR_HASH)?.readText()
                    ?: throw FileNotFoundException("Function jar ${FUNCTION_JAR_HASH} not found"))

    private fun uploadFunctionCode() {
        logger.message("Updating function's code...")
        val functionCodeResource = getFunctionCodeResource()

        val hash = getHash()

        val functionCode = kotlin.io.path.createTempFile().toFile()
        functionCode.writeBytes(functionCodeResource)

        val props = mapOf(Pair(CHECKSUM_KEY, hash))
        workingDirectoryTransfer.upload(lambdaFunctionName, functionCode, props)
        logger.message("Updated function's code successfully")
    }

    private fun getFunctionCodeResource() = (javaClass.classLoader.getResource(FUNCTION_JAR)?.readBytes()
            ?: throw FileNotFoundException("Function jar ${FUNCTION_JAR} not found"))

    override fun updateFunction() {
        uploadFunctionCode()
        val updateFunctionCodeRequest = UpdateFunctionCodeRequest().apply {
            functionName = lambdaFunctionName
            s3Bucket = workingDirectoryTransfer.bucketName
            s3Key = lambdaFunctionName
            publish = true
        }

        awsLambda.updateFunctionCode(updateFunctionCodeRequest)
        awaitFunctionStatus()
    }

    override fun doesFunctionNeedUpdate(function: GetFunctionResult): Boolean {
        val hash = getHash()

        val props = workingDirectoryTransfer.getValueProps(lambdaFunctionName)

        return props?.getUserMetaDataOf(CHECKSUM_KEY) != hash
    }

    companion object {
        private const val FUNCTION_JAR = "aws-lambda-function-all.jar"
        internal const val FUNCTION_JAR_HASH = "aws-lambda-function.jar.sha512"
        internal const val CHECKSUM_KEY = "checksum"
    }
}