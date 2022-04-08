import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.GetRoleRequest
import com.amazonaws.services.identitymanagement.model.GetUserResult
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException
import com.amazonaws.services.identitymanagement.model.User
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.clouds.amazon.connector.AwsConnectorFactory
import jetbrains.buildServer.clouds.amazon.connector.utils.parameters.AwsCloudConnectorConstants
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.LambdaPropertiesProcessor
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager
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
    private lateinit var projectManager: ProjectManager
    private lateinit var oAuthConnectionsManager: OAuthConnectionsManager
    private lateinit var awsConnectorFactory: AwsConnectorFactory
    private lateinit var project: SProject
    private lateinit var connectionDescriptor: OAuthConnectionDescriptor
    private lateinit var credentialsProvider: AWSCredentialsProvider
    private lateinit var credentials: AWSCredentials

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
        projectManager = m.mock(ProjectManager::class.java)
        oAuthConnectionsManager = m.mock(OAuthConnectionsManager::class.java)
        awsConnectorFactory = m.mock(AwsConnectorFactory::class.java)
        project = m.mock(SProject::class.java)
        connectionDescriptor = m.mock(OAuthConnectionDescriptor::class.java)
        credentialsProvider = m.mock(AWSCredentialsProvider::class.java)
        credentials = BasicAWSCredentials(ACCESS_KEY_ID, SECRET_ACCESS_KEY)
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

    private fun ensureCredentialsProvided() {
        val parameters = mapOf(
                Pair(AwsCloudConnectorConstants.REGION_NAME_PARAM, REGION_NAME)
        )
        m.checking(object : Expectations() {
            init {
                oneOf(projectManager).findProjectByExternalId(PROJECT_ID)
                will(returnValue(project))
                oneOf(oAuthConnectionsManager).findConnectionById(project, CONNECTION_ID)
                will(returnValue(connectionDescriptor))
                allowing(connectionDescriptor).parameters
                will(returnValue(parameters))
                oneOf(awsConnectorFactory).buildAwsCredentialsProvider(parameters)
                will(returnValue(credentialsProvider))
                allowing(credentialsProvider).credentials
                will(returnValue(credentials))
            }
        })
    }

    private fun ensureCredentialsAreInjected(properties: Map<String, String>) {
        Assert.assertTrue(properties.containsKey(LambdaConstants.AWS_ACCESS_KEY_ID))
        Assert.assertEquals(properties[LambdaConstants.AWS_ACCESS_KEY_ID], ACCESS_KEY_ID)
        Assert.assertTrue(properties.containsKey(LambdaConstants.AWS_SECRET_ACCESS_KEY))
        Assert.assertEquals(properties[LambdaConstants.AWS_SECRET_ACCESS_KEY], SECRET_ACCESS_KEY)
        Assert.assertTrue(properties.containsKey(LambdaConstants.AWS_REGION))
        Assert.assertEquals(properties[LambdaConstants.AWS_REGION], REGION_NAME)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess() {
        verifyIamRole()
        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.isEmpty())
        ensureCredentialsAreInjected(properties)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_ServiceEndpointUrl() {
        verifyIamRole()
        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = mutableMapOf(Pair(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM, URL))
        properties.putAll(createDefaultProperties())

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.isEmpty())
        ensureCredentialsAreInjected(properties)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_BadServiceEndpointUrl() {
        verifyIamRole()
        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = mutableMapOf(Pair(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM, BAD_URL))
        properties.putAll(createDefaultProperties())

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM, LambdaConstants.LAMBDA_ENDPOINT_URL_ERROR)))
        ensureCredentialsAreInjected(properties)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoScriptContent() {
        verifyIamRole()
        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties.remove(LambdaConstants.SCRIPT_CONTENT_PARAM)

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.SCRIPT_CONTENT_PARAM, LambdaConstants.SCRIPT_CONTENT_ERROR)))
        ensureCredentialsAreInjected(properties)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoMemorySize() {
        verifyIamRole()
        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties.remove(LambdaConstants.MEMORY_SIZE_PARAM)

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.MEMORY_SIZE_PARAM, LambdaConstants.MEMORY_SIZE_ERROR)))
        ensureCredentialsAreInjected(properties)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_MemorySizeNotNumber() {
        verifyIamRole()
        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties[LambdaConstants.MEMORY_SIZE_PARAM] = "error"

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.MEMORY_SIZE_PARAM, LambdaConstants.MEMORY_SIZE_VALUE_ERROR)))
        ensureCredentialsAreInjected(properties)
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
        ensureCredentialsProvided()
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
        ensureCredentialsAreInjected(properties)
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
        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        properties.remove(LambdaConstants.IAM_ROLE_PARAM)

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.IAM_ROLE_PARAM, LambdaConstants.IAM_ROLE_ERROR)))
        ensureCredentialsAreInjected(properties)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_IamRoleDefaultOption() {
        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        properties[LambdaConstants.IAM_ROLE_PARAM] = LambdaConstants.IAM_ROLE_SELECT_OPTION

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.IAM_ROLE_PARAM, LambdaConstants.IAM_ROLE_ERROR)))
        ensureCredentialsAreInjected(properties)
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

        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        properties[LambdaConstants.IAM_ROLE_PARAM] = LambdaConstants.IAM_ROLE_SELECT_OPTION

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.IAM_ROLE_PARAM, LambdaConstants.IAM_ROLE_ERROR)))
        ensureCredentialsAreInjected(properties)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoProjectId() {
        verifyIamRole()
        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()
        properties.remove(LambdaConstants.PROJECT_ID_PARAM)

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.PROJECT_ID_PARAM, "No project property found")))
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoProjectFound() {
        verifyIamRole()
        m.checking(object : Expectations() {
            init {
                oneOf(projectManager).findProjectByExternalId(PROJECT_ID)
                will(returnValue(null))
            }
        })
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.PROJECT_ID_PARAM, "No project $PROJECT_ID found")))
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoConnectionId() {
        verifyIamRole()
        ensureCredentialsProvided()
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()
        properties.remove(AwsCloudConnectorConstants.CHOSEN_AWS_CONN_ID_PARAM)

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(AwsCloudConnectorConstants.CHOSEN_AWS_CONN_ID_PARAM, "No connection has been chosen")))
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoConnectionFound() {
        verifyIamRole()
        m.checking(object : Expectations() {
            init {
                oneOf(projectManager).findProjectByExternalId(PROJECT_ID)
                will(returnValue(project))
                oneOf(oAuthConnectionsManager).findConnectionById(project, CONNECTION_ID)
                will(returnValue(null))
            }
        })
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(AwsCloudConnectorConstants.CHOSEN_AWS_CONN_ID_PARAM, "No connection $CONNECTION_ID found")))
    }

    private fun createPropertiesProcessor() = LambdaPropertiesProcessor(projectManager, oAuthConnectionsManager, awsConnectorFactory) { _, _ ->
        iam
    }

    private fun createDefaultProperties(): MutableMap<String, String> =
            mutableMapOf(
                    Pair(LambdaConstants.SCRIPT_CONTENT_PARAM, SCRIPT_CONTENT),
                    Pair(LambdaConstants.MEMORY_SIZE_PARAM, MEMORY_SIZE),
                    Pair(LambdaConstants.IAM_ROLE_PARAM, IAM_ROLE),
                    Pair(LambdaConstants.PROJECT_ID_PARAM, PROJECT_ID),
                    Pair(LambdaConstants.STORAGE_SIZE_PARAM, STORAGE_SIZE),
                    Pair(AwsCloudConnectorConstants.CHOSEN_AWS_CONN_ID_PARAM, CONNECTION_ID)
            )

    companion object {
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

        const val PROJECT_ID = "projectId"
        const val CONNECTION_ID = "connectionId"
    }
}