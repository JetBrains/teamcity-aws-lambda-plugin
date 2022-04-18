package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.model.*
import jetbrains.buildServer.runner.lambda.LambdaConstants
import org.jmock.Expectations
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.Test

class EcrImageLambdaFunctionResolverTest : BaseFunctionResolverTestCase(LAMBDA_FUNCTION_NAME) {

    @Test
    fun testResolveFunction() {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = LAMBDA_FUNCTION_NAME
                })
                will(returnValue(GetFunctionResult().apply {
                    configuration = FunctionConfiguration().apply {
                        memorySize = MEMORY_SIZE.toInt()
                        role = IAM_ROLE_ARN
                        ephemeralStorage = EphemeralStorage().apply {
                            size = STORAGE_SIZE.toInt()
                        }
                    }
                    code = mockUpdateChecks()
                }))
            }
        })

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
    }

    override fun mockUpdateChecks(): FunctionCodeLocation = FunctionCodeLocation().apply {
        imageUri = ECR_IMAGE_URI
    }


    @Test
    fun testResolveFunction_FunctionNotFound() {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = LAMBDA_FUNCTION_NAME
                })
                will(throwException(resourceNotFoundException))
                oneOf(resourceNotFoundException).fillInStackTrace()
            }
        })


        expectCreateFunction()
        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
    }


    @Test
    fun testResolveFunction_FunctionNotFound_RoleNotFound() {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = LAMBDA_FUNCTION_NAME
                })
                will(throwException(resourceNotFoundException))
                oneOf(resourceNotFoundException).fillInStackTrace()
            }
        })


        expectCreateFunction()
        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
    }

    private fun expectCreateFunction() {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).createFunction(CreateFunctionRequest().apply {
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
        })
    }


    private fun verifyCodeIsChanged() {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).updateFunctionCode(UpdateFunctionCodeRequest().apply {
                    functionName = LAMBDA_FUNCTION_NAME
                    imageUri = ECR_IMAGE_URI
                    publish = true
                })
            }
        })
    }

    @Test
    fun testResolveFunction_DifferentImage() {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = LAMBDA_FUNCTION_NAME
                })
                will(returnValue(GetFunctionResult().apply {
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
                }))
            }
        })

        verifyCodeIsChanged()
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
    }

    @Test
    fun testResolveFunction_ImageEndingInLatest() {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = LAMBDA_FUNCTION_NAME
                })
                will(returnValue(GetFunctionResult().apply {
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
                }))
            }
        })

        verifyCodeIsChanged()
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
    }

    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }

    override fun createClient() = EcrImageLambdaFunctionResolver(MEMORY_SIZE.toInt(), IAM_ROLE_ARN, STORAGE_SIZE.toInt(), ECR_IMAGE_URI, logger, awsLambda, LAMBDA_FUNCTION_NAME, workingDirectoryTransfer)

    companion object {
        const val LAMBDA_FUNCTION_NAME = "${LambdaConstants.FUNCTION_NAME}-$ECR_IMAGE_URI"
    }
}