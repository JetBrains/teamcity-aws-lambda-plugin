package jetbrains.buildServer.runner.lambda.function

import MockLoggerObject.mockBuildLogger
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
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class LambdaFunctionResolverFactoryImplTest: BaseTestCase(){
    protected lateinit var m: Mockery
    protected lateinit var context: BuildRunnerContext
    protected lateinit var awsLambda: AWSLambda
    protected lateinit var awsLambdaException: AWSLambdaException
    protected lateinit var resourceNotFoundException: ResourceNotFoundException
    protected lateinit var waiters: AWSLambdaWaiters
    protected lateinit var waiter: Waiter<GetFunctionRequest>
    protected lateinit var logger: Logger
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

    @Test
    fun testGetLambdaFunctionResolver_DefaultImage(){
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

        val lambdaFunctionResolverFactory = createClient()
        val resolver = lambdaFunctionResolverFactory.getLambdaFunctionResolver()
        Assert.assertTrue(resolver is DefaultImageLambdaFunctionResolver)
    }

    @Test
    fun testGetLambdaFunctionResolver_EcrImage(){
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