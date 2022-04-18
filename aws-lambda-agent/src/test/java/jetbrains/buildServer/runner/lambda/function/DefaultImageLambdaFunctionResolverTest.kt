package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.model.*
import com.amazonaws.services.s3.model.ObjectMetadata
import jetbrains.buildServer.runner.lambda.LambdaConstants
import org.jmock.Expectations
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.Test
import java.io.File

class DefaultImageLambdaFunctionResolverTest : BaseFunctionResolverTestCase(LAMBDA_FUNCTION_NAME) {

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
                }))
            }
        })

        expectFunctionCodeUpdateCheck()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
    }

    @Test
    fun testResolveFunction_CodeNotFound() {
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
                }))
            }
        })

        m.checking(object : Expectations() {
            init {
                oneOf(workingDirectoryTransfer).getValueProps(LAMBDA_FUNCTION_NAME)
                will(returnValue(null))
            }
        })

        expectUploadFunctionCode()
        expectUpdateFunctionCode()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
    }

    @Test
    fun testResolveFunction_DifferentCode() {
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
                }))
            }
        })

        m.checking(object : Expectations() {
            init {
                oneOf(workingDirectoryTransfer).getValueProps(LAMBDA_FUNCTION_NAME)
                will(returnValue(ObjectMetadata().apply {
                    addUserMetadata(DefaultImageLambdaFunctionResolver.CHECKSUM_KEY, "differentHash")
                }))
            }
        })

        expectUploadFunctionCode()
        expectUpdateFunctionCode()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, LAMBDA_FUNCTION_NAME)
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
        expectUploadFunctionCode()
        m.checking(object : Expectations() {
            init {
                oneOf(workingDirectoryTransfer).bucketName
                will(returnValue(BUCKET_NAME))

                oneOf(awsLambda).createFunction(CreateFunctionRequest().apply {
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
        })
    }


    private fun expectUploadFunctionCode() {
        m.checking(object : Expectations() {
            init {
                oneOf(workingDirectoryTransfer).upload(
                        with(LAMBDA_FUNCTION_NAME),
                        with(any(File::class.java)),
                        with(mapOf(Pair(DefaultImageLambdaFunctionResolver.CHECKSUM_KEY, getHash()!!)))
                )
            }
        })
    }

    private fun expectUpdateFunctionCode(){
        m.checking(object : Expectations(){
            init {
                oneOf(workingDirectoryTransfer).bucketName
                will(returnValue(BUCKET_NAME))
                oneOf(awsLambda).updateFunctionCode(UpdateFunctionCodeRequest().apply {
                    functionName = LAMBDA_FUNCTION_NAME
                    s3Bucket = BUCKET_NAME
                    s3Key = LAMBDA_FUNCTION_NAME
                    publish = true
                })
            }
        })
        mockAwaitFunctionUpdates()
    }

    private fun expectFunctionCodeUpdateCheck() {
        val hash = getHash()

        m.checking(object : Expectations() {
            init {
                oneOf(workingDirectoryTransfer).getValueProps(LAMBDA_FUNCTION_NAME)
                will(returnValue(ObjectMetadata().apply {
                    addUserMetadata(DefaultImageLambdaFunctionResolver.CHECKSUM_KEY, hash)
                }))
            }
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
            workingDirectoryTransfer)

    override fun mockUpdateChecks(): FunctionCodeLocation? {
        expectFunctionCodeUpdateCheck()
        return null
    }

    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }

    companion object {
        const val MEMORY_SIZE = "512"
        const val STORAGE_SIZE = "1024"
        const val BUCKET_NAME = "bucketName"
        const val IAM_ROLE_ARN =
                "${LambdaConstants.IAM_PREFIX}::accountId:role/${LambdaConstants.DEFAULT_LAMBDA_ARN_NAME}"
        const val LAMBDA_FUNCTION_NAME = "${LambdaConstants.FUNCTION_NAME}-${LambdaConstants.DEFAULT_LAMBDA_RUNTIME}"

    }
}