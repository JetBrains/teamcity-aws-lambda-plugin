package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.*
import com.amazonaws.services.lambda.waiters.AWSLambdaWaiters
import com.amazonaws.waiters.Waiter
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransfer
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.Listeners
import org.testng.annotations.Test

@Listeners(MockitoTestNGListener::class)
abstract class BaseFunctionResolverTestCase(private val lambdaFunctionName: String) : BaseTestCase() {
    @Mock
    protected lateinit var context: BuildRunnerContext

    @Mock
    protected lateinit var awsLambda: AWSLambda

    @Mock
    protected lateinit var awsLambdaException: AWSLambdaException

    @Mock
    protected lateinit var resourceNotFoundException: ResourceNotFoundException

    @Mock
    protected lateinit var waiters: AWSLambdaWaiters

    @Mock
    protected lateinit var waiter: Waiter<GetFunctionRequest>

    @Mock
    protected lateinit var logger: Logger

    @Mock
    protected lateinit var workingDirectoryTransfer: S3WorkingDirectoryTransfer

    protected fun mockAwaitFunctionUpdates() {
        whenever(awsLambda.waiters()).thenReturn(waiters)
        whenever(waiters.functionActiveV2()).thenReturn(waiter)
        whenever(waiters.functionUpdatedV2()).thenReturn(waiter)
    }

    protected fun verifyAwaitFunctionUpdates() {
        Mockito.verify(waiter, times(2)).run(any())
    }


    private fun verifyConfigurationIsChanged(lambdaFunctionName: String) {
        Mockito.verify(awsLambda).updateFunctionConfiguration(UpdateFunctionConfigurationRequest().apply {
            functionName = lambdaFunctionName
            memorySize = MEMORY_SIZE.toInt()
            role = IAM_ROLE_ARN
            ephemeralStorage = EphemeralStorage().apply {
                size = STORAGE_SIZE.toInt()
            }
        })
    }

    @Test
    fun testResolveFunction_DifferentMemorySize() {
        val memory = MEMORY_SIZE.toInt() + 1
        whenever(awsLambda.getFunction(GetFunctionRequest().apply {
            functionName = lambdaFunctionName
        })).thenReturn(GetFunctionResult().apply {
            configuration = FunctionConfiguration().apply {
                memorySize = memory
                role = IAM_ROLE_ARN
                ephemeralStorage = EphemeralStorage().apply {
                    size = STORAGE_SIZE.toInt()
                }
            }
            code = getFunctionCodeLocation()
        })

        mockUpdateChecks()
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
        verifyConfigurationIsChanged(lambdaFunctionName)
        verifyAwaitFunctionUpdates()
    }

    @Test
    fun testResolveFunction_DifferentStorageSize() {
        val storage = STORAGE_SIZE.toInt() + 1
        whenever(awsLambda.getFunction(GetFunctionRequest().apply {
            functionName = lambdaFunctionName
        })).thenReturn(GetFunctionResult().apply {
            configuration = FunctionConfiguration().apply {
                memorySize = MEMORY_SIZE.toInt()
                role = IAM_ROLE_ARN
                ephemeralStorage = EphemeralStorage().apply {
                    size = storage
                }
            }
            code = getFunctionCodeLocation()
        })

        mockUpdateChecks()
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
        verifyConfigurationIsChanged(lambdaFunctionName)
        verifyAwaitFunctionUpdates()
    }

    @Test
    fun testResolveFunction_DifferentRole() {
        val iamRole = "${IAM_ROLE_ARN}-different"
        whenever(awsLambda.getFunction(GetFunctionRequest().apply {
            functionName = lambdaFunctionName
        })).thenReturn(GetFunctionResult().apply {
            configuration = FunctionConfiguration().apply {
                memorySize = MEMORY_SIZE.toInt()
                role = iamRole
                ephemeralStorage = EphemeralStorage().apply {
                    size = STORAGE_SIZE.toInt()
                }
            }
            code = getFunctionCodeLocation()
        })

        mockUpdateChecks()
        mockAwaitFunctionUpdates()
        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
        verifyConfigurationIsChanged(lambdaFunctionName)
        verifyAwaitFunctionUpdates()
    }

    @Test
    fun testResolveFunction_LocalFunction() {
        whenever(awsLambda.getFunction(GetFunctionRequest().apply {
            functionName = lambdaFunctionName
        })).thenThrow(awsLambdaException)
        whenever(awsLambdaException.statusCode).thenReturn(404)

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    abstract fun createClient(): LambdaFunctionResolverEx

    abstract fun mockUpdateChecks()

    abstract fun getFunctionCodeLocation(): FunctionCodeLocation?

    companion object {
        const val MEMORY_SIZE = "512"
        const val STORAGE_SIZE = "1024"
        const val ECR_IMAGE_URI = "ecrImageUri"
        const val IAM_ROLE_ARN =
            "${LambdaConstants.IAM_PREFIX}::accountId:role/${LambdaConstants.DEFAULT_LAMBDA_ARN_NAME}"
        const val BUCKET_NAME = "bucketName"
    }
}