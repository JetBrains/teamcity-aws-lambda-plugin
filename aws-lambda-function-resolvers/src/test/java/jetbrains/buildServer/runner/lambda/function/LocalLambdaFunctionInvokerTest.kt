package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.InvocationType
import com.amazonaws.services.lambda.model.InvokeRequest
import com.fasterxml.jackson.databind.ObjectMapper
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.LambdaConstants.FUNCTION_NAME
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.model.BuildDetails
import jetbrains.buildServer.runner.lambda.model.RunDetails
import org.junit.Assert
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import java.util.concurrent.atomic.AtomicBoolean

@Listeners(MockitoTestNGListener::class)
class LocalLambdaFunctionInvokerTest : BaseTestCase() {
    @Mock
    private lateinit var logger: Logger

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var awsLambda: AWSLambda

    @Mock
    private lateinit var lambdaFunctionResolverFactory: LambdaFunctionResolverFactory

    @Mock
    private lateinit var lambdaFunctionResolver: LambdaFunctionResolver


    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()

        whenever(lambdaFunctionResolverFactory.getLambdaFunctionResolver())
            .thenReturn(lambdaFunctionResolver)
        whenever(lambdaFunctionResolver.resolveFunction())
            .thenReturn(FUNCTION_NAME)
        whenever(objectMapper.writeValueAsString(RUN_DETAILS))
            .thenReturn(OBJECT_STRING)
    }

    @Test
    fun testInvokeLambdaFunction() {
        val invoker = createClient(AtomicBoolean())


        val error = invoker.invokeLambdaFunction(listOf(RUN_DETAILS))
        Assert.assertFalse(error)
        Mockito.verify(
            awsLambda
        ).invoke(
            InvokeRequest().withInvocationType(InvocationType.Event).withFunctionName(FUNCTION_NAME)
                .withPayload(
                    OBJECT_STRING
                )
        )
    }

    @Test
    fun testInvokeLambdaFunction_InterruptedBuild() {
        val invoker = createClient(AtomicBoolean(true))

        val error = invoker.invokeLambdaFunction(listOf(RUN_DETAILS))
        Assert.assertTrue(error)
    }

    private fun createClient(atomicBoolean: AtomicBoolean): LocalLambdaFunctionInvoker =
        LocalLambdaFunctionInvoker(logger, objectMapper, atomicBoolean, awsLambda, lambdaFunctionResolverFactory)

    companion object {
        private const val OBJECT_STRING = "objectString"
        private const val USERNAME = "username"
        private const val PASSWORD = "password"
        private const val URL = "url"
        private const val BUILD_ID = "buildId"
        private const val CUSTOM_SCRIPT_FILENAME = "customScriptFilename"
        private const val DIRECTORY_ID = "directoryId"
        private const val RUN_NUMBER = 0
        private const val BUILD_TYPE_ID = "buildTypeId"
        private const val AGENT_NAME = "agentName"
        private val RUN_DETAILS = RunDetails(
            USERNAME,
            PASSWORD,
            URL,
            CUSTOM_SCRIPT_FILENAME,
            DIRECTORY_ID,
            RUN_NUMBER,
            BuildDetails(
                BUILD_ID,
                BUILD_TYPE_ID,
                AGENT_NAME
            )
        )
    }
}