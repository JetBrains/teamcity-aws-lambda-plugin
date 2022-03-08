import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.LambdaRunType
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.RunTypeRegistry
import jetbrains.buildServer.serverSide.ServerSettings
import jetbrains.buildServer.util.amazon.AWSCommonParams
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.springframework.web.servlet.mvc.Controller
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class LambdaRunTypeTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var registry: RunTypeRegistry
    private lateinit var descriptor: PluginDescriptor
    private lateinit var controllerManager: WebControllerManager
    private lateinit var serverSettings: ServerSettings

    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        registry = m.mock(RunTypeRegistry::class.java)
        descriptor = m.mock(PluginDescriptor::class.java)
        controllerManager = m.mock(WebControllerManager::class.java)
        serverSettings = m.mock(ServerSettings::class.java)

        m.checking(object : Expectations() {
            init {
                allowing(descriptor).getPluginResourcesPath(LambdaConstants.EDIT_PARAMS_HTML)
                will(returnValue(RESOLVED_HTML_EDIT_PATH))
                allowing(descriptor).getPluginResourcesPath(LambdaConstants.VIEW_PARAMS_HTML)
                will(returnValue(RESOLVED_HTML_VIEW_PATH))
                allowing(serverSettings).serverUUID
                will(returnValue(SERVER_UUID))
                allowing(descriptor).getPluginResourcesPath(LambdaConstants.VIEW_PARAMS_JSP)
                allowing(descriptor).getPluginResourcesPath(LambdaConstants.EDIT_PARAMS_JSP)
                allowing(controllerManager).registerController(
                    with(same(RESOLVED_HTML_EDIT_PATH)),
                    with(any(Controller::class.java))
                )
                allowing(controllerManager).registerController(
                    with(same(RESOLVED_HTML_VIEW_PATH)),
                    with(any(Controller::class.java))
                )

                oneOf(registry).registerRunType(with(any(LambdaRunType::class.java)))
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
    @Throws(Exception::class)
    fun testGetEditRunnerParamsJspFilePath() {
        val runType = createRunType()
        Assert.assertEquals(runType.editRunnerParamsJspFilePath, RESOLVED_HTML_EDIT_PATH)
    }

    @Test
    @Throws(Exception::class)
    fun testGetViewRunnerParamsJspFilePath() {
        val runType = createRunType()
        Assert.assertEquals(runType.viewRunnerParamsJspFilePath, RESOLVED_HTML_VIEW_PATH)
    }

    @Test
    @Throws(Exception::class)
    fun testGetDefaultRunnerProperties() {
        val runType = createRunType()
        Assert.assertEquals(runType.defaultRunnerProperties, AWSCommonParams.getDefaults(SERVER_UUID))
    }

    @Test
    @Throws(Exception::class)
    fun testGetRunnerPropertiesProcessor() {
        val runType = createRunType()
        val propertiesProcessor = runType.runnerPropertiesProcessor

        val invalidProperties = propertiesProcessor.process(createAWSCommonProperties())
        Assert.assertTrue(invalidProperties.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun testGetRunnerPropertiesProcessor_ServiceEndpointUrl() {
        val runType = createRunType()
        val propertiesProcessor = runType.runnerPropertiesProcessor

        val properties = mutableMapOf(
            Pair(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM, URL)
        )
        properties.putAll(createAWSCommonProperties())

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.isEmpty())
    }


    @Test
    @Throws(Exception::class)
    fun testGetRunnerPropertiesProcessor_BadServiceEndpointUrl() {
        val runType = createRunType()
        val propertiesProcessor = runType.runnerPropertiesProcessor

        val properties = mutableMapOf(
            Pair(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM, BAD_URL)
        )
        properties.putAll(createAWSCommonProperties())

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM,
                    LambdaConstants.LAMBDA_ENDPOINT_URL_ERROR
                )
            )
        )
    }

    private fun createRunType() = LambdaRunType(registry, descriptor, controllerManager, serverSettings)

    private fun createAWSCommonProperties(): Map<String, String> {
        val properties = AWSCommonParams.getDefaults(SERVER_UUID)
        properties[AWSCommonParams.SECURE_SECRET_ACCESS_KEY_PARAM] = SECRET_ACCESS_KEY
        properties[AWSCommonParams.ACCESS_KEY_ID_PARAM] = ACCESS_KEY_ID
        properties[AWSCommonParams.REGION_NAME_PARAM] = REGION_NAME

        return properties
    }

    companion object {
        const val RESOLVED_HTML_EDIT_PATH = "resolvedHtmlEditPath"
        const val RESOLVED_HTML_VIEW_PATH = "resolvedHtmlViewPath"
        const val SERVER_UUID = "serverUuid"
        const val SECRET_ACCESS_KEY = "secretAccessKey"
        const val ACCESS_KEY_ID = "accessKeyId"
        const val REGION_NAME = "regionName"

        const val URL = "http://localhost"
        const val BAD_URL = "badUrl"
    }
}