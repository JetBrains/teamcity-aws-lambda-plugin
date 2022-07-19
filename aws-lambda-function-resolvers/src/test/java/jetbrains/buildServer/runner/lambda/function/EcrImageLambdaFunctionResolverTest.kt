package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.model.*
import jetbrains.buildServer.runner.lambda.LambdaConstants
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.testng.Assert
import org.testng.annotations.Test

class EcrImageLambdaFunctionResolverTest : BaseFunctionResolverTestCase(LAMBDA_FUNCTION_NAME) {

    @Test
    fun testResolveFunction() {
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
            code = getFunctionCodeLocation()
        })

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
    }

    override fun mockUpdateChecks() {}
    override fun getFunctionCodeLocation(): FunctionCodeLocation = FunctionCodeLocation().apply {
        imageUri = ECR_IMAGE_URI
    }


    @Test
    fun testResolveFunction_FunctionNotFound() {
        whenever(awsLambda.getFunction(GetFunctionRequest().apply {
            functionName = LAMBDA_FUNCTION_NAME
        })).thenThrow(resourceNotFoundException)

        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
        expectCreateFunction()
    }


    @Test
    fun testResolveFunction_FunctionNotFound_RoleNotFound() {
        whenever(awsLambda.getFunction(GetFunctionRequest().apply {
            functionName = LAMBDA_FUNCTION_NAME
        })).thenThrow(resourceNotFoundException)

        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
        expectCreateFunction()
    }

    private fun expectCreateFunction() {
        Mockito.verify(awsLambda).createFunction(CreateFunctionRequest().apply {
            functionName = LAMBDA_FUNCTION_NAME
            code = FunctionCode().apply {
                imageUri = ECR_IMAGE_URI
            }
            role = IAM_ROLE_ARN
            ephemeralStorage = EphemeralStorage().apply {
                size = STORAGE_SIZE.toInt()
            }
            publish = true
            packageType = "Image"
            memorySize = MEMORY_SIZE.toInt()
            timeout = LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT
        })
    }


    private fun verifyCodeIsChanged() {
        Mockito.verify(awsLambda).updateFunctionCode(UpdateFunctionCodeRequest().apply {
            functionName = LAMBDA_FUNCTION_NAME
            imageUri = ECR_IMAGE_URI
            publish = true
        })
    }

    @Test
    fun testResolveFunction_DifferentImage() {
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
            code = FunctionCodeLocation().apply {
                imageUri = "${ECR_IMAGE_URI}-different"
            }
        })

        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
        verifyCodeIsChanged()
    }

    @Test
    fun testResolveFunction_ImageEndingInLatest() {
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
            code = FunctionCodeLocation().apply {
                imageUri = "${ECR_IMAGE_URI}:latest"
            }
        })

        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
        verifyCodeIsChanged()
    }

    override fun createClient() =
        EcrImageLambdaFunctionResolver(MEMORY_SIZE.toInt(), IAM_ROLE_ARN, STORAGE_SIZE.toInt(), ECR_IMAGE_URI, logger, awsLambda, LAMBDA_FUNCTION_NAME, workingDirectoryTransfer)

    companion object {
        const val LAMBDA_FUNCTION_NAME = "${LambdaConstants.FUNCTION_NAME}-$ECR_IMAGE_URI"
    }
}