package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.*
import com.amazonaws.services.lambda.waiters.AWSLambdaWaiters
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.waiters.Waiter
import com.amazonaws.waiters.WaiterParameters
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.MockLoggerObject.mockBuildLogger
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransfer
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File

class LambdaFunctionResolverImplTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var context: BuildRunnerContext
    private lateinit var awsLambda: AWSLambda
    private lateinit var awsLambdaException: AWSLambdaException
    private lateinit var resourceNotFoundException: ResourceNotFoundException
    private lateinit var waiters: AWSLambdaWaiters
    private lateinit var waiter: Waiter<GetFunctionRequest>
    private lateinit var logger: BuildProgressLogger
    private lateinit var workingDirectoryTransfer: S3WorkingDirectoryTransfer

    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        context = m.mock(BuildRunnerContext::class.java)
        awsLambda = m.mock(AWSLambda::class.java)
        awsLambdaException = m.mock(AWSLambdaException::class.java)
        resourceNotFoundException = m.mock(ResourceNotFoundException::class.java)
        waiters = m.mock(AWSLambdaWaiters::class.java)
        waiter = m.mock(Waiter::class.java) as Waiter<GetFunctionRequest>
        logger = m.mockBuildLogger()
        workingDirectoryTransfer = m.mock(S3WorkingDirectoryTransfer::class.java)
    }

    private fun mockRunnerParameters(): String {
        val lambdaFunctionName = "${LambdaConstants.FUNCTION_NAME}-$ECR_IMAGE_URI"
        m.checking(object : Expectations() {
            init {
                allowing(context).runnerParameters
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.MEMORY_SIZE_PARAM, MEMORY_SIZE),
                            Pair(LambdaConstants.STORAGE_SIZE_PARAM, STORAGE_SIZE),
                            Pair(LambdaConstants.ECR_IMAGE_URI_PARAM, ECR_IMAGE_URI),
                            Pair(LambdaConstants.IAM_ROLE_PARAM, IAM_ROLE_ARN)
                        )
                    )
                )
            }
        })

        return lambdaFunctionName
    }

    private fun mockDefaultImage(): String {
        val lambdaFunctionName = "${LambdaConstants.FUNCTION_NAME}-${LambdaConstants.DEFAULT_LAMBDA_RUNTIME}"

        m.checking(object : Expectations() {
            init {
                allowing(context).runnerParameters
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.MEMORY_SIZE_PARAM, MEMORY_SIZE),
                            Pair(LambdaConstants.STORAGE_SIZE_PARAM, STORAGE_SIZE),
                            Pair(LambdaConstants.IAM_ROLE_PARAM, IAM_ROLE_ARN),

                        )
                    )
                )
            }
        })
        return lambdaFunctionName
    }

    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }

    @Test
    fun testResolveFunction() {
        val lambdaFunctionName = mockRunnerParameters()
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
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
                        imageUri = ECR_IMAGE_URI
                    }
                }))
            }
        })

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DefaultImage_CodeNotFound() {
        val lambdaFunctionName = mockDefaultImage()

        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
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
                oneOf(workingDirectoryTransfer).getValueProps(lambdaFunctionName)
                will(returnValue(null))
            }
        })

        expectUploadFunctionCode(lambdaFunctionName)
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }


    @Test
    fun testResolveFunction_DefaultImage_DifferentCode() {
        val lambdaFunctionName = mockDefaultImage()

        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
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
                oneOf(workingDirectoryTransfer).getValueProps(lambdaFunctionName)
                will(returnValue(ObjectMetadata().apply {
                    addUserMetadata(LambdaFunctionResolverImpl.CHECKSUM_KEY, "differentHash")
                }))
            }
        })

        expectUploadFunctionCode(lambdaFunctionName)
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    private fun expectFunctionCodeUpdateCheck(lambdaFunctionName: String) {
        val hash = getHash()

        m.checking(object : Expectations() {
            init {
                oneOf(workingDirectoryTransfer).getValueProps(lambdaFunctionName)
                will(returnValue(ObjectMetadata().apply {
                    addUserMetadata(LambdaFunctionResolverImpl.CHECKSUM_KEY, hash)
                }))
            }
        })
    }

    private fun getHash() = javaClass.classLoader.getResource(LambdaFunctionResolverImpl.FUNCTION_JAR_HASH)?.readText()

    @Test
    fun testResolveFunction_DefaultImage() {
        val lambdaFunctionName = mockDefaultImage()

        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
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

        expectFunctionCodeUpdateCheck(lambdaFunctionName)
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    private fun verifyConfigurationIsChanged(lambdaFunctionName: String) {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).updateFunctionConfiguration(UpdateFunctionConfigurationRequest().apply {
                    functionName = lambdaFunctionName
                    memorySize = MEMORY_SIZE.toInt()
                    role = IAM_ROLE_ARN
                    ephemeralStorage = EphemeralStorage().apply {
                        size = STORAGE_SIZE.toInt()
                    }
                })
            }
        })
    }

    @Test
    fun testResolveFunction_DifferentMemorySize() {
        val lambdaFunctionName = mockRunnerParameters()
        val memory = MEMORY_SIZE.toInt() + 1
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(returnValue(GetFunctionResult().apply {
                    configuration = FunctionConfiguration().apply {
                        memorySize = memory
                        role = IAM_ROLE_ARN
                        ephemeralStorage = EphemeralStorage().apply {
                            size = STORAGE_SIZE.toInt()
                        }
                    }
                    code = FunctionCodeLocation().apply {
                        imageUri = ECR_IMAGE_URI
                    }
                }))
            }
        })

        verifyConfigurationIsChanged(lambdaFunctionName)
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DefaultImage_DifferentMemorySize() {
        val lambdaFunctionName = mockDefaultImage()
        val memory = MEMORY_SIZE.toInt() + 1

        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(returnValue(GetFunctionResult().apply {
                    configuration = FunctionConfiguration().apply {
                        memorySize = memory
                        role = IAM_ROLE_ARN
                        ephemeralStorage = EphemeralStorage().apply {
                            size = STORAGE_SIZE.toInt()
                        }
                    }
                }))
            }
        })

        verifyConfigurationIsChanged(lambdaFunctionName)
        expectFunctionCodeUpdateCheck(lambdaFunctionName)
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DifferentStorageSize() {
        val lambdaFunctionName = mockRunnerParameters()
        val storage = STORAGE_SIZE.toInt() + 1
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(returnValue(GetFunctionResult().apply {
                    configuration = FunctionConfiguration().apply {
                        memorySize = MEMORY_SIZE.toInt()
                        role = IAM_ROLE_ARN
                        ephemeralStorage = EphemeralStorage().apply {
                            size = storage
                        }
                    }
                    code = FunctionCodeLocation().apply {
                        imageUri = ECR_IMAGE_URI
                    }
                }))
            }
        })

        verifyConfigurationIsChanged(lambdaFunctionName)
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DefaultImage_DifferentStorageSize() {
        val lambdaFunctionName = mockDefaultImage()
        val storage = STORAGE_SIZE.toInt() + 1

        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(returnValue(GetFunctionResult().apply {
                    configuration = FunctionConfiguration().apply {
                        memorySize = MEMORY_SIZE.toInt()
                        role = IAM_ROLE_ARN
                        ephemeralStorage = EphemeralStorage().apply {
                            size = storage
                        }
                    }
                }))
            }
        })

        verifyConfigurationIsChanged(lambdaFunctionName)
        expectFunctionCodeUpdateCheck(lambdaFunctionName)
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DifferentRole() {
        val lambdaFunctionName = mockRunnerParameters()
        val iamRole = "$IAM_ROLE_ARN-different"
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(returnValue(GetFunctionResult().apply {
                    configuration = FunctionConfiguration().apply {
                        memorySize = MEMORY_SIZE.toInt()
                        role = iamRole
                        ephemeralStorage = EphemeralStorage().apply {
                            size = STORAGE_SIZE.toInt()
                        }
                    }
                    code = FunctionCodeLocation().apply {
                        imageUri = ECR_IMAGE_URI
                    }
                }))
            }
        })

        verifyConfigurationIsChanged(lambdaFunctionName)
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DefaultImage_DifferentRole() {
        val lambdaFunctionName = mockDefaultImage()
        val iamRole = "$IAM_ROLE_ARN-different"
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(returnValue(GetFunctionResult().apply {
                    configuration = FunctionConfiguration().apply {
                        memorySize = MEMORY_SIZE.toInt()
                        role = iamRole
                        ephemeralStorage = EphemeralStorage().apply {
                            size = STORAGE_SIZE.toInt()
                        }
                    }
                }))
            }
        })

        verifyConfigurationIsChanged(lambdaFunctionName)
        expectFunctionCodeUpdateCheck(lambdaFunctionName)
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    private fun verifyCodeIsChanged(lambdaFunctionName: String) {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).updateFunctionCode(UpdateFunctionCodeRequest().apply {
                    functionName = lambdaFunctionName
                    imageUri = ECR_IMAGE_URI
                    publish = true
                })
            }
        })
    }

    @Test
    fun testResolveFunction_DifferentImage() {
        val lambdaFunctionName = mockRunnerParameters()
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
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
                        imageUri = "$ECR_IMAGE_URI-different"
                    }
                }))
            }
        })

        verifyCodeIsChanged(lambdaFunctionName)
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_ImageEndingInLatest() {
        val lambdaFunctionName = mockRunnerParameters()
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
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
                        imageUri = "$ECR_IMAGE_URI:latest"
                    }
                }))
            }
        })

        verifyCodeIsChanged(lambdaFunctionName)
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_LocalFunction() {
        val lambdaFunctionName = mockRunnerParameters()
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(awsLambdaException))
                oneOf(awsLambdaException).fillInStackTrace()
                oneOf(awsLambdaException).statusCode
                will(returnValue(404))
            }
        })

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DefaultImage_LocalFunction() {
        val lambdaFunctionName = mockDefaultImage()

        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(awsLambdaException))
                oneOf(awsLambdaException).fillInStackTrace()
                oneOf(awsLambdaException).statusCode
                will(returnValue(404))
            }
        })

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }


    private fun mockAwaitFunctionUpdates() {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).waiters()
                will(returnValue(waiters))
                oneOf(waiters).functionActiveV2()
                will(returnValue(waiter))
                oneOf(waiters).functionUpdatedV2()
                will(returnValue(waiter))
                val waiterClass = WaiterParameters::class.java as Class<WaiterParameters<GetFunctionRequest>>
                exactly(2).of(waiter).run(with(any(waiterClass)))
            }
        })
    }

    @Test
    fun testResolveFunction_FunctionNotFound() {
        val lambdaFunctionName = mockRunnerParameters()
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(resourceNotFoundException))
                oneOf(resourceNotFoundException).fillInStackTrace()
            }
        })


        expectCreateFunction(lambdaFunctionName)
        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }


    @Test
    fun testResolveFunction_FunctionNotFound_RoleNotFound() {
        val lambdaFunctionName = mockRunnerParameters()
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(resourceNotFoundException))
                oneOf(resourceNotFoundException).fillInStackTrace()
            }
        })


        expectCreateFunction(lambdaFunctionName)
        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    private fun expectCreateFunction(lambdaFunctionName: String) {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).createFunction(CreateFunctionRequest().apply {
                    functionName = lambdaFunctionName
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

    @Test
    fun testResolveFunction_DefaultImage_FunctionNotFound() {
        val lambdaFunctionName = mockDefaultImage()

        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(resourceNotFoundException))
                oneOf(resourceNotFoundException).fillInStackTrace()
            }
        })


        expectCreateFunctionWithDefaultImage(lambdaFunctionName)
        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DefaultImage_FunctionNotFound_RoleNotFound() {
        val lambdaFunctionName = mockDefaultImage()

        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(resourceNotFoundException))
                oneOf(resourceNotFoundException).fillInStackTrace()
            }
        })


        expectCreateFunctionWithDefaultImage(lambdaFunctionName)
        mockAwaitFunctionUpdates()

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    private fun expectUploadFunctionCode(lambdaFunctionName: String){
        m.checking(object : Expectations(){
            init {
                oneOf(workingDirectoryTransfer).upload(
                    with(lambdaFunctionName),
                    with(any(File::class.java)),
                    with(mapOf(Pair(LambdaFunctionResolverImpl.CHECKSUM_KEY, getHash()!!)))
                )
            }
        })
    }

    private fun expectCreateFunctionWithDefaultImage(lambdaFunctionName: String) {
        expectUploadFunctionCode(lambdaFunctionName)
        m.checking(object : Expectations() {
            init {
                oneOf(workingDirectoryTransfer).bucketName
                will(returnValue(BUCKET_NAME))

                oneOf(awsLambda).createFunction(CreateFunctionRequest().apply {
                    functionName = lambdaFunctionName
                    code = FunctionCode().apply {
                        s3Bucket = BUCKET_NAME
                        s3Key = lambdaFunctionName
                        handler = LambdaConstants.FUNCTION_HANDLER
                    }
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

    private fun createClient() = LambdaFunctionResolverImpl(context, logger, awsLambda, workingDirectoryTransfer)

    companion object {
        const val MEMORY_SIZE = "512"
        const val STORAGE_SIZE = "1024"
        const val ECR_IMAGE_URI = "ecrImageUri"
        const val IAM_ROLE_ARN =
            "${LambdaConstants.IAM_PREFIX}::accountId:role/${LambdaConstants.DEFAULT_LAMBDA_ARN_NAME}"
        const val BUCKET_NAME = "bucketName"
    }
}