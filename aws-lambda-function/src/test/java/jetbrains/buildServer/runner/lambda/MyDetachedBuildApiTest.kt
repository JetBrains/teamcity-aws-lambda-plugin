package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import jetbrains.buildServer.BaseTestCase
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
        runDetails = RunDetails(USERNAME, PASSWORD, BUILD_ID, TEAMCITY_URl, ENV_PARAMS, SCRIPT_CONTENT)
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
                Assert.assertEquals((request.body as TextContent).text, SERVICE_MESSAGE)
                respond(
                    content = "",
                )
            }
            val detachedBuildApi = createClient()
            detachedBuildApi.logAsync(SERVICE_MESSAGE).join()
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

    private fun createClient() = MyDetachedBuildApi(runDetails, context, engine)

    companion object {
        const val TEAMCITY_URl = "http://teamcityUrl"
        const val BUILD_ID = "buildId"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        val ENV_PARAMS = emptyMap<String, String>()
        const val SCRIPT_CONTENT = "scriptContent"


        const val SERVICE_MESSAGE = "serviceMessage"
    }
}