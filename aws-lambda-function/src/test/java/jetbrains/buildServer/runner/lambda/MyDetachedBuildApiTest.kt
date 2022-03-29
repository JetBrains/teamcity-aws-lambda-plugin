package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.build.ProcessFailedException
import kotlinx.coroutines.runBlocking
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class MyDetachedBuildApiTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var runDetails: RunDetails
    private lateinit var context: Context
    private lateinit var engine: HttpClientEngine
    private lateinit var logger: LambdaLogger

    @BeforeMethod
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        runDetails = RunDetails(USERNAME, PASSWORD, BUILD_ID, TEAMCITY_URl, ENV_PARAMS, SCRIPT_CONTENT, DIRECTORY_ID)
        context = m.mock(Context::class.java)
        logger = m.mock(LambdaLogger::class.java)

        m.checking(object : Expectations() {
            init {
                allowing(context).logger
                will(returnValue(logger))
                allowing(logger).log(with(any(String::class.java)))
                allowing(logger).log(with(any(ByteArray::class.java)))
            }
        })
    }


    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }

    @Test
    fun testLog() {
        runBlocking {
            engine = MockEngine { request ->
                Assert.assertTrue(request.url.toString().startsWith(TEAMCITY_URl))
                Assert.assertTrue(request.url.toString().endsWith("$BUILD_ID/log"))
                Assert.assertEquals(request.method, HttpMethod.Post)
                Assert.assertEquals(request.body::class.java, TextContent::class.java)
                Assert.assertEquals(
                    (request.body as TextContent).text,
                    "#teamcity[$MESSAGE flowId='AWS Lambda' text='$SERVICE_MESSAGE')"
                )
                respond(
                    content = "",
                )
            }
            val detachedBuildApi = createClient()
            detachedBuildApi.log(SERVICE_MESSAGE)
        }
    }

    @Test
    fun testFinishBuild() {
        runBlocking {
            engine = MockEngine { request ->
                Assert.assertTrue(request.url.toString().startsWith(TEAMCITY_URl))
                Assert.assertTrue(request.url.toString().endsWith("$BUILD_ID/finish"))
                Assert.assertEquals(request.method, HttpMethod.Put)
                respond(
                    content = "",
                )
            }
            val detachedBuildApi = createClient()
            detachedBuildApi.finishBuild()
        }
    }

    @Test
    fun testFailBuild() {
        runBlocking {
            engine = MockEngine { request ->
                Assert.assertTrue(request.url.toString().startsWith(TEAMCITY_URl))
                Assert.assertTrue(request.url.toString().endsWith("$BUILD_ID/log"))
                Assert.assertEquals(request.method, HttpMethod.Post)
                Assert.assertEquals(request.body::class.java, TextContent::class.java)
                Assert.assertEquals(
                    (request.body as TextContent).text,
                    "##teamcity[buildProblem flowId='AWS Lambda' description='$DESCRIPTION']"
                )
                respond(
                    content = "",
                )
            }
            val detachedBuildApi = createClient()
            detachedBuildApi.failBuild(ProcessFailedException(DESCRIPTION))
        }
    }

    @Test
    fun testFailBuild_ErrorId() {
        runBlocking {
            engine = MockEngine { request ->
                Assert.assertTrue(request.url.toString().startsWith(TEAMCITY_URl))
                Assert.assertTrue(request.url.toString().endsWith("$BUILD_ID/log"))
                Assert.assertEquals(request.method, HttpMethod.Post)
                Assert.assertEquals(request.body::class.java, TextContent::class.java)
                Assert.assertEquals(
                    (request.body as TextContent).text,
                    "##teamcity[buildProblem flowId='AWS Lambda' description='$DESCRIPTION' identity='$ERROR_ID']"
                )
                respond(
                    content = "",
                )
            }
            val detachedBuildApi = createClient()
            detachedBuildApi.failBuild(ProcessFailedException(DESCRIPTION), ERROR_ID)
        }
    }

    @Test
    fun testLogWarning() {
        runBlocking {
            engine = MockEngine { request ->
                Assert.assertTrue(request.url.toString().startsWith(TEAMCITY_URl))
                Assert.assertTrue(request.url.toString().endsWith("$BUILD_ID/log"))
                Assert.assertEquals(request.method, HttpMethod.Post)
                Assert.assertEquals(request.body::class.java, TextContent::class.java)
                Assert.assertEquals(
                    (request.body as TextContent).text,
                    "##teamcity[message flowId='AWS Lambda' text='$MESSAGE' status='WARNING']"
                )
                respond(
                    content = "",
                )
            }
            val detachedBuildApi = createClient()
            detachedBuildApi.logWarning(MESSAGE)
        }
    }

    @Test
    fun testStartLogging() {
        runBlocking {
            engine = MockEngine { request ->
                Assert.assertTrue(request.url.toString().startsWith(TEAMCITY_URl))
                Assert.assertTrue(request.url.toString().endsWith("$BUILD_ID/log"))
                Assert.assertEquals(request.method, HttpMethod.Post)
                Assert.assertEquals(request.body::class.java, TextContent::class.java)
                Assert.assertEquals(
                    (request.body as TextContent).text,
                    "##teamcity[blockOpened flowId='AWS Lambda' name='AWS Lambda' description='AWS Lambda Execution']"
                )
                respond(
                    content = "",
                )
            }
            val detachedBuildApi = createClient()
            detachedBuildApi.startLogging()
        }
    }

    @Test
    fun testEndLogging() {
        runBlocking {
            engine = MockEngine { request ->
                Assert.assertTrue(request.url.toString().startsWith(TEAMCITY_URl))
                Assert.assertTrue(request.url.toString().endsWith("$BUILD_ID/log"))
                Assert.assertEquals(request.method, HttpMethod.Post)
                Assert.assertEquals(request.body::class.java, TextContent::class.java)
                Assert.assertEquals(
                    (request.body as TextContent).text,
                    "##teamcity[blockClosed flowId='AWS Lambda' name='AWS Lambda']"
                )
                respond(
                    content = "",
                )
            }
            val detachedBuildApi = createClient()
            detachedBuildApi.stopLogging()
        }
    }

    @Test
    fun testGetServiceMessage_EscapedValues() {
        engine = MockEngine { respond("") }
        val detachedBuildApi = createClient()
        val params = mapOf(Pair(DESCRIPTION, ESCAPED_VALUES_MESSAGE))
        val serviceMessage = detachedBuildApi.getServiceMessage(MESSAGE, params)

        val expectedServiceMessage =
            "##teamcity[$MESSAGE flowId='AWS Lambda' $DESCRIPTION='$EXPECTED_ESCAPED_VALUES_MESSAGE']"
        Assert.assertEquals(serviceMessage, expectedServiceMessage)
    }

    private fun createClient() = MyDetachedBuildApi(runDetails, context, engine)

    companion object {
        private const val TEAMCITY_URl = "http://teamcityUrl"
        private const val BUILD_ID = "buildId"
        private const val USERNAME = "username"
        private const val PASSWORD = "password"
        private val ENV_PARAMS = emptyMap<String, String>()
        private const val SCRIPT_CONTENT = "scriptContent"
        private const val DIRECTORY_ID = "directoryId"


        private const val SERVICE_MESSAGE = "serviceMessage"
        private const val DESCRIPTION = "description"
        private const val ERROR_ID = "errorId"
        private const val MESSAGE = "message"
        private const val ESCAPED_VALUES_MESSAGE = "| ' [ ] \n"
        private const val EXPECTED_ESCAPED_VALUES_MESSAGE = "|| |' |[ |] |\n"
    }
}