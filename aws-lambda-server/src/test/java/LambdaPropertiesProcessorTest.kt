import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.LambdaPropertiesProcessor
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.util.amazon.AWSCommonParams
import org.testng.Assert
import org.testng.annotations.Test

class LambdaPropertiesProcessorTest : BaseTestCase() {

    @Test
    @Throws(Exception::class)
    fun testProcess() {
        val propertiesProcessor = createPropertiesProcessor()

        val invalidProperties = propertiesProcessor.process(createDefaultProperties())
        Assert.assertTrue(invalidProperties.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_ServiceEndpointUrl() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = mutableMapOf(
            Pair(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM, URL)
        )
        properties.putAll(createDefaultProperties())

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_BadServiceEndpointUrl() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = mutableMapOf(
            Pair(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM, BAD_URL)
        )
        properties.putAll(createDefaultProperties())

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

    @Test
    @Throws(Exception::class)
    fun testProcess_NoScriptContent() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties.remove(LambdaConstants.SCRIPT_CONTENT_PARAM)

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.SCRIPT_CONTENT_PARAM,
                    LambdaConstants.SCRIPT_CONTENT_ERROR
                )
            )
        )
    }

    private fun createPropertiesProcessor() = LambdaPropertiesProcessor()

    private fun createDefaultProperties(): MutableMap<String, String> {
        val properties = AWSCommonParams.getDefaults(SERVER_UUID)
        properties[AWSCommonParams.SECURE_SECRET_ACCESS_KEY_PARAM] = SECRET_ACCESS_KEY
        properties[AWSCommonParams.ACCESS_KEY_ID_PARAM] = ACCESS_KEY_ID
        properties[AWSCommonParams.REGION_NAME_PARAM] = REGION_NAME
        properties[LambdaConstants.SCRIPT_CONTENT_PARAM] = SCRIPT_CONTENT
        return properties
    }

    companion object {
        const val SERVER_UUID = "serverUuid"
        const val SECRET_ACCESS_KEY = "secretAccessKey"
        const val ACCESS_KEY_ID = "accessKeyId"
        const val REGION_NAME = "regionName"
        const val URL = "http://localhost"

        const val BAD_URL = "badUrl"
        const val SCRIPT_CONTENT = "scriptContent"
    }
}