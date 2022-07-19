package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.model.*
import com.amazonaws.services.s3.model.ObjectMetadata
import jetbrains.buildServer.runner.lambda.LambdaConstants
import org.jmock.Expectations
import org.junit.Before
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.testng.Assert
import org.testng.annotations.Test

class DefaultImageLambdaFunctionResolverTest : BaseFunctionResolverTestCase(LAMBDA_FUNCTION_NAME) {

    @Test
    fun testResolveFunction() {
        whenever(awsLambda.getFunction(GetFunctionRequest().apply {
            functionName = LAMBDA_FUNCTION_NAME
        })).thenReturn(GetFunctionResult().apply {
            configuration = FunctionConfiguration().apply {
                memorySize = MEMORY_SIZE.toInt()
                role = IAM_ROLE_ARN
                ephemeralStorage = EphemeralStorage().apply {
                    size = STORAGE_SIZE.toInt()
                }
            }
        })

        expectFunctionCodeUpdateCheck()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
    }

    @Test
    fun testResolveFunction_CodeNotFound() {
        whenever(awsLambda.getFunction(GetFunctionRequest().apply {
            functionName = LAMBDA_FUNCTION_NAME
        }))
            .thenReturn(GetFunctionResult().apply {
                configuration = FunctionConfiguration().apply {
                    memorySize = MEMORY_SIZE.toInt()
                    role = IAM_ROLE_ARN
                    ephemeralStorage = EphemeralStorage().apply {
                        size = STORAGE_SIZE.toInt()
                    }
                }
            })

        whenever(workingDirectoryTransfer.getValueProps(LAMBDA_FUNCTION_NAME))
            .thenReturn(null)

        mockBucketName()
        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
        expectUploadFunctionCode()
        expectUpdateFunctionCode()
    }

    @Test
    fun testResolveFunction_DifferentCode() {
        whenever(
            awsLambda.getFunction(GetFunctionRequest().apply {
                functionName = LAMBDA_FUNCTION_NAME
            })
        ).thenReturn(GetFunctionResult().apply {
            configuration = FunctionConfiguration().apply {
                memorySize = MEMORY_SIZE.toInt()
                role = IAM_ROLE_ARN
                ephemeralStorage = EphemeralStorage().apply {
                    size = STORAGE_SIZE.toInt()
                }
            }
        })

        whenever(workingDirectoryTransfer.getValueProps(LAMBDA_FUNCTION_NAME))
            .thenReturn(ObjectMetadata().apply {
                addUserMetadata(DefaultImageLambdaFunctionResolver.CHECKSUM_KEY, "differentHash")
            })

        mockBucketName()
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
        expectUploadFunctionCode()
        expectUpdateFunctionCode()
    }

    @Test
    fun testResolveFunction_FunctionNotFound() {
        whenever(
            awsLambda.getFunction(GetFunctionRequest().apply {
                functionName = LAMBDA_FUNCTION_NAME
            })
        ).thenThrow(resourceNotFoundException)


        mockBucketName()
        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
        verifyAwaitFunctionUpdates()
        expectCreateFunction()
    }

    @Test
    fun testResolveFunction_FunctionNotFound_RoleNotFound() {
        whenever(
            awsLambda.getFunction(GetFunctionRequest().apply {
                functionName = LAMBDA_FUNCTION_NAME
            })
        ).thenThrow(resourceNotFoundException)

        mockBucketName()
        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
        verifyAwaitFunctionUpdates()
        expectCreateFunction()
    }


    private fun mockBucketName() {
        whenever(workingDirectoryTransfer.bucketName).thenReturn(BUCKET_NAME)
    }

    private fun expectCreateFunction() {
        expectUploadFunctionCode()
        Mockito.verify(awsLambda).createFunction(CreateFunctionRequest().apply {
            functionName = LAMBDA_FUNCTION_NAME
            code = FunctionCode().apply {
                s3Bucket = BUCKET_NAME
                s3Key = LAMBDA_FUNCTION_NAME
            }
            handler = LambdaConstants.FUNCTION_HANDLER
            role = IAM_ROLE_ARN
            publish = true
            ephemeralStorage = EphemeralStorage().apply {
                size = STORAGE_SIZE.toInt()
            }
            packageType = "Zip"
            runtime = LambdaConstants.DEFAULT_LAMBDA_RUNTIME
            memorySize = MEMORY_SIZE.toInt()
            timeout = LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT
        })
    }


    private fun expectUploadFunctionCode() {
        Mockito.verify(workingDirectoryTransfer).upload(
            org.mockito.kotlin.eq(LAMBDA_FUNCTION_NAME),
            any(),
            Mockito.eq(mapOf(Pair(DefaultImageLambdaFunctionResolver.CHECKSUM_KEY, getHash()!!)))
        )
    }

    private fun expectUpdateFunctionCode() {
        Mockito.verify(awsLambda).updateFunctionCode(UpdateFunctionCodeRequest().apply {
            functionName = LAMBDA_FUNCTION_NAME
            s3Bucket = BUCKET_NAME
            s3Key = LAMBDA_FUNCTION_NAME
            publish = true
        })
    }

    private fun expectFunctionCodeUpdateCheck() {
        val hash = getHash()
        whenever(workingDirectoryTransfer.getValueProps(LAMBDA_FUNCTION_NAME)).thenReturn(
            ObjectMetadata().apply {
                addUserMetadata(DefaultImageLambdaFunctionResolver.CHECKSUM_KEY, hash)
            })
    }

    private fun getHash() = javaClass.classLoader.getResource(DefaultImageLambdaFunctionResolver.FUNCTION_JAR_HASH)?.readText()

    override fun createClient() = DefaultImageLambdaFunctionResolver(
        MEMORY_SIZE.toInt(),
        IAM_ROLE_ARN,
        STORAGE_SIZE.toInt(),
        LambdaConstants.DEFAULT_LAMBDA_RUNTIME,
        logger,
        awsLambda,
        LAMBDA_FUNCTION_NAME,
        workingDirectoryTransfer
    )

    override fun mockUpdateChecks() {
        expectFunctionCodeUpdateCheck()
    }

    override fun getFunctionCodeLocation(): FunctionCodeLocation? = null

    companion object {
        const val MEMORY_SIZE = "512"
        const val STORAGE_SIZE = "1024"
        const val BUCKET_NAME = "bucketName"
        const val IAM_ROLE_ARN =
            "${LambdaConstants.IAM_PREFIX}::accountId:role/${LambdaConstants.DEFAULT_LAMBDA_ARN_NAME}"
        const val LAMBDA_FUNCTION_NAME = "${LambdaConstants.FUNCTION_NAME}-${LambdaConstants.DEFAULT_LAMBDA_RUNTIME}"

    }
}