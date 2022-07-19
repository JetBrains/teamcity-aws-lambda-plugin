package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.AWSLambdaException
import com.amazonaws.services.lambda.model.GetFunctionRequest
import com.amazonaws.services.lambda.model.ResourceNotFoundException
import com.amazonaws.services.lambda.waiters.AWSLambdaWaiters
import com.amazonaws.waiters.Waiter
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransfer
import org.jmock.Mockery
import org.mockito.Mock
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.Listeners
import org.testng.annotations.Test

@Listeners(MockitoTestNGListener::class)
class LambdaFunctionResolverFactoryImplTest : BaseTestCase() {
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

    @Test
    fun testGetLambdaFunctionResolver_DefaultImage() {
        whenever(context.runnerParameters).thenReturn(
            mapOf(
                Pair(LambdaConstants.MEMORY_SIZE_PARAM, MEMORY_SIZE),
                Pair(LambdaConstants.STORAGE_SIZE_PARAM, STORAGE_SIZE),
                Pair(LambdaConstants.IAM_ROLE_PARAM, IAM_ROLE_ARN),

                )
        )

        val lambdaFunctionResolverFactory = createClient()
        val resolver = lambdaFunctionResolverFactory.getLambdaFunctionResolver()
        Assert.assertTrue(resolver is DefaultImageLambdaFunctionResolver)
    }

    @Test
    fun testGetLambdaFunctionResolver_EcrImage() {
        whenever(context.runnerParameters).thenReturn(
            mapOf(
                Pair(LambdaConstants.MEMORY_SIZE_PARAM, MEMORY_SIZE),
                Pair(LambdaConstants.STORAGE_SIZE_PARAM, STORAGE_SIZE),
                Pair(LambdaConstants.ECR_IMAGE_URI_PARAM, ECR_IMAGE_URI),
                Pair(LambdaConstants.IAM_ROLE_PARAM, IAM_ROLE_ARN)
            )
        )

        val lambdaFunctionResolverFactory = createClient()
        val resolver = lambdaFunctionResolverFactory.getLambdaFunctionResolver()
        Assert.assertTrue(resolver is EcrImageLambdaFunctionResolver)
    }

    private fun createClient(): LambdaFunctionResolverFactory = LambdaFunctionResolverFactoryImpl(logger, awsLambda, workingDirectoryTransfer, context.runnerParameters)

    companion object {
        const val MEMORY_SIZE = "512"
        const val STORAGE_SIZE = "1024"
        const val ECR_IMAGE_URI = "ecrImageUri"
        const val IAM_ROLE_ARN =
            "${LambdaConstants.IAM_PREFIX}::accountId:role/${LambdaConstants.DEFAULT_LAMBDA_ARN_NAME}"
    }
}