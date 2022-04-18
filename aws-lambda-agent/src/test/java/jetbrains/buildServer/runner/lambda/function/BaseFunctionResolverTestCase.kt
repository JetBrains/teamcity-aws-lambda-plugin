package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.*
import com.amazonaws.services.lambda.waiters.AWSLambdaWaiters
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
import org.testng.annotations.*

abstract class BaseFunctionResolverTestCase(private val lambdaFunctionName: String): BaseTestCase() {
    protected lateinit var m: Mockery
    protected lateinit var context: BuildRunnerContext
    protected lateinit var awsLambda: AWSLambda
    protected lateinit var awsLambdaException: AWSLambdaException
    protected lateinit var resourceNotFoundException: ResourceNotFoundException
    protected lateinit var waiters: AWSLambdaWaiters
    protected lateinit var waiter: Waiter<GetFunctionRequest>
    protected lateinit var logger: BuildProgressLogger
    protected lateinit var workingDirectoryTransfer: S3WorkingDirectoryTransfer

    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }

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

    protected fun mockAwaitFunctionUpdates() {
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
                    code = mockUpdateChecks()
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
    fun testResolveFunction_DifferentStorageSize() {
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
                    code = mockUpdateChecks()
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
    fun testResolveFunction_DifferentRole() {
        val iamRole = "${IAM_ROLE_ARN}-different"
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
                    code = mockUpdateChecks()
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
    fun testResolveFunction_LocalFunction() {
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

    abstract fun createClient(): LambdaFunctionResolverEx

    abstract fun mockUpdateChecks(): FunctionCodeLocation?

    companion object {
        const val MEMORY_SIZE = "512"
        const val STORAGE_SIZE = "1024"
        const val ECR_IMAGE_URI = "ecrImageUri"
        const val IAM_ROLE_ARN =
                "${LambdaConstants.IAM_PREFIX}::accountId:role/${LambdaConstants.DEFAULT_LAMBDA_ARN_NAME}"
        const val BUCKET_NAME = "bucketName"
    }
}