import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.GetRoleRequest
import com.amazonaws.services.identitymanagement.model.GetUserResult
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException
import com.amazonaws.services.identitymanagement.model.User
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.LambdaPropertiesProcessor
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.util.amazon.AWSCommonParams
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class LambdaPropertiesProcessorTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var iam: AmazonIdentityManagement
    private lateinit var userResult: GetUserResult
    private lateinit var user: User

    @BeforeMethod
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        iam = m.mock(AmazonIdentityManagement::class.java)
        userResult = m.mock(GetUserResult::class.java)
        user = m.mock(User::class.java)
    }

    private fun verifyIamRole() {
        m.checking(object : Expectations() {
            init {
                oneOf(iam).user
                will(returnValue(userResult))
                oneOf(userResult).user
                will(returnValue(user))
                oneOf(user).arn
                will(returnValue(USER_ARN))

                oneOf(iam).getRole(GetRoleRequest().apply {
                    roleName = IAM_ROLE_NAME
                })
            }
        })
    }

    @Test
    @Throws(Exception::class)
    fun testProcess() {
        verifyIamRole()
        val propertiesProcessor = createPropertiesProcessor()

        val invalidProperties = propertiesProcessor.process(createDefaultProperties())
        Assert.assertTrue(invalidProperties.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_ServiceEndpointUrl() {
        verifyIamRole()
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
        verifyIamRole()
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
        verifyIamRole()
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

    @Test
    @Throws(Exception::class)
    fun testProcess_NoMemorySize() {
        verifyIamRole()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties.remove(LambdaConstants.MEMORY_SIZE_PARAM)

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.MEMORY_SIZE_PARAM,
                    LambdaConstants.MEMORY_SIZE_ERROR
                )
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_MemorySizeNotNumber() {
        verifyIamRole()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties[LambdaConstants.MEMORY_SIZE_PARAM] = "error"

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.MEMORY_SIZE_PARAM,
                    LambdaConstants.MEMORY_SIZE_VALUE_ERROR
                )
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_MemorySizeNotValidMemory() {
        verifyIamRole()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties[LambdaConstants.MEMORY_SIZE_PARAM] = (LambdaConstants.MAX_MEMORY_SIZE + 1).toString()

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.MEMORY_SIZE_PARAM,
                    LambdaConstants.MEMORY_SIZE_VALUE_ERROR
                )
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_StorageSizeNotNumber() {
        verifyIamRole()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties[LambdaConstants.STORAGE_SIZE_PARAM] = "error"

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.STORAGE_SIZE_PARAM,
                    LambdaConstants.STORAGE_SIZE_VALUE_ERROR
                )
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_StorageSizeNotValidMemory() {
        verifyIamRole()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties[LambdaConstants.STORAGE_SIZE_PARAM] = (LambdaConstants.MAX_STORAGE_SIZE + 1).toString()

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.STORAGE_SIZE_PARAM,
                    LambdaConstants.STORAGE_SIZE_VALUE_ERROR
                )
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_IamRoleNotFound() {
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        properties.remove(LambdaConstants.IAM_ROLE_PARAM)

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.IAM_ROLE_PARAM,
                    LambdaConstants.IAM_ROLE_ERROR
                )
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_IamRoleDefaultOption() {
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        properties[LambdaConstants.IAM_ROLE_PARAM] = LambdaConstants.IAM_ROLE_SELECT_OPTION

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.IAM_ROLE_PARAM,
                    LambdaConstants.IAM_ROLE_ERROR
                )
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NonExistingIamRole() {
        m.checking(object : Expectations() {
            init {
                oneOf(iam).user
                will(returnValue(userResult))
                oneOf(userResult).user
                will(returnValue(user))
                oneOf(user).arn
                will(returnValue(USER_ARN))

                oneOf(iam).getRole(GetRoleRequest().apply {
                    roleName = IAM_ROLE_NAME
                })
                will(throwException(NoSuchEntityException("mock")))
            }
        })

        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        properties[LambdaConstants.IAM_ROLE_PARAM] = LambdaConstants.IAM_ROLE_SELECT_OPTION

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.IAM_ROLE_PARAM,
                    LambdaConstants.IAM_ROLE_ERROR
                )
            )
        )
    }

    private fun createPropertiesProcessor() = LambdaPropertiesProcessor {
        iam
    }

    private fun createDefaultProperties(): MutableMap<String, String> {
        val properties = AWSCommonParams.getDefaults(SERVER_UUID)
        properties[AWSCommonParams.SECURE_SECRET_ACCESS_KEY_PARAM] = SECRET_ACCESS_KEY
        properties[AWSCommonParams.ACCESS_KEY_ID_PARAM] = ACCESS_KEY_ID
        properties[AWSCommonParams.REGION_NAME_PARAM] = REGION_NAME
        properties[LambdaConstants.SCRIPT_CONTENT_PARAM] = SCRIPT_CONTENT
        properties[LambdaConstants.MEMORY_SIZE_PARAM] = MEMORY_SIZE
        properties[LambdaConstants.STORAGE_SIZE_PARAM] = STORAGE_SIZE
        properties[LambdaConstants.IAM_ROLE_PARAM] = IAM_ROLE
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
        const val MEMORY_SIZE = "512"
        const val STORAGE_SIZE = "1024"

        const val USER_ARN = "${LambdaConstants.IAM_PREFIX}::accountId:user"
        const val IAM_ROLE_NAME = "iamRole"
        const val IAM_ROLE = "${LambdaConstants.IAM_PREFIX}::accountId:role/$IAM_ROLE_NAME"
    }
}