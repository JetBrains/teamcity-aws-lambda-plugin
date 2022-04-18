package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.InvocationType
import com.amazonaws.services.lambda.model.InvokeRequest
import com.fasterxml.jackson.databind.ObjectMapper
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.runner.lambda.LambdaConstants.FUNCTION_NAME
import jetbrains.buildServer.runner.lambda.MockLoggerObject.mockBuildLogger
import jetbrains.buildServer.runner.lambda.RunDetails
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.concurrent.atomic.AtomicBoolean

class LocalLambdaFunctionInvokerTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var logger: BuildProgressLogger
    private lateinit var objectMapper: ObjectMapper
    private lateinit var awsLambda: AWSLambda
    private lateinit var lambdaFunctionResolverFactory: LambdaFunctionResolverFactory
    private lateinit var lambdaFunctionResolver: LambdaFunctionResolver


    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        logger = m.mockBuildLogger()
        objectMapper = m.mock(ObjectMapper::class.java)
        awsLambda = m.mock(AWSLambda::class.java)
        lambdaFunctionResolverFactory = m.mock(LambdaFunctionResolverFactory::class.java)
        lambdaFunctionResolver = m.mock(LambdaFunctionResolver::class.java)


        m.checking(object : Expectations() {
            init {
                oneOf(lambdaFunctionResolverFactory).getLambdaFunctionResolver()
                will(returnValue(lambdaFunctionResolver))
                oneOf(lambdaFunctionResolver).resolveFunction()
                will(returnValue(FUNCTION_NAME))

                allowing(objectMapper).writeValueAsString(RUN_DETAILS)
                will(returnValue(OBJECT_STRING))

            }
        })
    }

    @Test
    fun testInvokeLambdaFunction() {
        val invoker = createClient(AtomicBoolean())

        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).invoke(
                        InvokeRequest().withInvocationType(InvocationType.Event).withFunctionName(FUNCTION_NAME)
                                .withPayload(
                                        OBJECT_STRING
                                )
                )
            }
        })

        val error = invoker.invokeLambdaFunction(RUN_DETAILS)
        Assert.assertFalse(error)
    }

    @Test
    fun testInvokeLambdaFunction_InterruptedBuild() {
        val invoker = createClient(AtomicBoolean(true))

        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).invoke(
                        InvokeRequest().withInvocationType(InvocationType.Event).withFunctionName(FUNCTION_NAME)
                                .withPayload(
                                        OBJECT_STRING
                                )
                )
            }
        })

        val error = invoker.invokeLambdaFunction(RUN_DETAILS)
        Assert.assertTrue(error)
    }

    private fun createClient(atomicBoolean: AtomicBoolean): LocalLambdaFunctionInvoker = LocalLambdaFunctionInvoker(logger, objectMapper, atomicBoolean, awsLambda, lambdaFunctionResolverFactory)

    companion object {
        private const val OBJECT_STRING = "objectString"
        private const val USERNAME = "username"
        private const val PASSWORD = "password"
        private const val URL = "url"
        private const val BUILD_ID = "buildId"
        private const val CUSTOM_SCRIPT_FILENAME = "customScriptFilename"
        private const val DIRECTORY_ID = "directoryId"
        private val RUN_DETAILS = RunDetails(
                USERNAME,
                PASSWORD,
                BUILD_ID,
                URL,
                CUSTOM_SCRIPT_FILENAME,
                DIRECTORY_ID
        )
    }
}