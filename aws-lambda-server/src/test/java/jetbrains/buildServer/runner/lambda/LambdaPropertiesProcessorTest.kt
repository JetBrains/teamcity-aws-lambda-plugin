package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.GetRoleRequest
import com.amazonaws.services.identitymanagement.model.GetUserResult
import com.amazonaws.services.identitymanagement.model.User
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.clouds.amazon.connector.AwsCredentialsData
import jetbrains.buildServer.clouds.amazon.connector.AwsCredentialsHolder
import jetbrains.buildServer.clouds.amazon.connector.errors.features.LinkedAwsConnNotFoundException
import jetbrains.buildServer.clouds.amazon.connector.featureDevelopment.AwsConnectionsManager
import jetbrains.buildServer.clouds.amazon.connector.impl.dataBeans.AwsConnectionBean
import jetbrains.buildServer.clouds.amazon.connector.utils.parameters.AwsCloudConnectorConstants
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SProject
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.Listeners
import org.testng.annotations.Test

@Listeners(MockitoTestNGListener::class)
class LambdaPropertiesProcessorTest : BaseTestCase() {
    @Mock
    private lateinit var iam: AmazonIdentityManagement

    @Mock
    private lateinit var userResult: GetUserResult

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var projectManager: ProjectManager

    @Mock
    private lateinit var project: SProject

    @Mock
    private lateinit var awsConnectionsManager: AwsConnectionsManager

    @Mock
    private lateinit var awsConnectionBean: AwsConnectionBean

    @Mock
    private lateinit var awsCredentialsHolder: AwsCredentialsHolder

    @Mock
    private lateinit var awsCredentialsData: AwsCredentialsData

    private fun verifyIamRole() {
        Mockito.verify(iam).getRole(GetRoleRequest().apply {
            roleName = IAM_ROLE_NAME
        })

    }

    private fun mockIamRole() {
        whenever(iam.user).thenReturn(userResult)
        whenever(userResult.user).thenReturn(user)
        whenever(user.arn).thenReturn(USER_ARN)
    }

    private fun ensureCredentialsProvided(properties: Map<String, String>) {
        val awsCredentialsData = object : AwsCredentialsData {
            override fun getAccessKeyId(): String = ACCESS_KEY_ID

            override fun getSecretAccessKey(): String = SECRET_ACCESS_KEY

            override fun getSessionToken(): String? = null

        }

        ensureProjectIsProvided()
        whenever(awsConnectionsManager.getLinkedAwsConnection(properties, project))
            .thenReturn(awsConnectionBean)
        whenever(awsConnectionBean.awsCredentialsHolder)
            .thenReturn(awsCredentialsHolder)
        whenever(awsCredentialsHolder.awsCredentials)
            .thenReturn(awsCredentialsData)
        whenever(awsConnectionBean.region)
            .thenReturn(REGION_NAME)
        whenever(awsConnectionBean.isUsingSessionCredentials)
            .thenReturn(false)
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
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()
        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.isEmpty())
        ensureCredentialsAreInjected(properties)
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_ServiceEndpointUrl() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = mutableMapOf(Pair(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM, URL))
        properties.putAll(createDefaultProperties())

        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.isEmpty())
        ensureCredentialsAreInjected(properties)
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_BadServiceEndpointUrl() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = mutableMapOf(Pair(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM, BAD_URL))
        properties.putAll(createDefaultProperties())

        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM, LambdaConstants.LAMBDA_ENDPOINT_URL_ERROR)))
        ensureCredentialsAreInjected(properties)
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoScriptContent() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties.remove(LambdaConstants.SCRIPT_CONTENT_PARAM)

        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.SCRIPT_CONTENT_PARAM, LambdaConstants.SCRIPT_CONTENT_ERROR)))
        ensureCredentialsAreInjected(properties)
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoMemorySize() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties.remove(LambdaConstants.MEMORY_SIZE_PARAM)

        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.MEMORY_SIZE_PARAM, LambdaConstants.MEMORY_SIZE_ERROR)))
        ensureCredentialsAreInjected(properties)
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_MemorySizeNotNumber() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties[LambdaConstants.MEMORY_SIZE_PARAM] = "error"

        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.MEMORY_SIZE_PARAM, LambdaConstants.MEMORY_SIZE_VALUE_ERROR)))
        ensureCredentialsAreInjected(properties)
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_MemorySizeNotValidMemory() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties[LambdaConstants.MEMORY_SIZE_PARAM] = (LambdaConstants.MAX_MEMORY_SIZE + 1).toString()

        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.MEMORY_SIZE_PARAM,
                    LambdaConstants.MEMORY_SIZE_VALUE_ERROR
                )
            )
        )
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_StorageSizeNotNumber() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties[LambdaConstants.STORAGE_SIZE_PARAM] = "error"

        ensureCredentialsProvided(properties)
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
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_StorageSizeNotValidMemory() {
        val propertiesProcessor = createPropertiesProcessor()

        val properties = createDefaultProperties()

        properties[LambdaConstants.STORAGE_SIZE_PARAM] = (LambdaConstants.MAX_STORAGE_SIZE + 1).toString()

        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(
            invalidProperties.contains(
                InvalidProperty(
                    LambdaConstants.STORAGE_SIZE_PARAM,
                    LambdaConstants.STORAGE_SIZE_VALUE_ERROR
                )
            )
        )
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_IamRoleNotFound() {
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        properties.remove(LambdaConstants.IAM_ROLE_PARAM)

        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.IAM_ROLE_PARAM, LambdaConstants.IAM_ROLE_ERROR)))
        ensureCredentialsAreInjected(properties)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_IamRoleDefaultOption() {
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        properties[LambdaConstants.IAM_ROLE_PARAM] = LambdaConstants.IAM_ROLE_SELECT_OPTION

        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.IAM_ROLE_PARAM, LambdaConstants.IAM_ROLE_ERROR)))
        ensureCredentialsAreInjected(properties)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NonExistingIamRole() {
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        properties[LambdaConstants.IAM_ROLE_PARAM] = LambdaConstants.IAM_ROLE_SELECT_OPTION

        ensureCredentialsProvided(properties)
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.IAM_ROLE_PARAM, LambdaConstants.IAM_ROLE_ERROR)))
        ensureCredentialsAreInjected(properties)
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoProjectId() {
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()
        properties.remove(LambdaConstants.PROJECT_ID_PARAM)

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.PROJECT_ID_PARAM, "No project property found")))
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoProjectFound() {
        whenever(projectManager.findProjectByExternalId(PROJECT_ID))
            .thenReturn(null)
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(LambdaConstants.PROJECT_ID_PARAM, "No project $PROJECT_ID found")))
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoConnectionId() {
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()
        properties.remove(AwsCloudConnectorConstants.CHOSEN_AWS_CONN_ID_PARAM)

        ensureProjectIsProvided()
        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(AwsCloudConnectorConstants.CHOSEN_AWS_CONN_ID_PARAM, "No connection has been chosen")))
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_NoConnectionFound() {
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        ensureProjectIsProvided()
        whenever(awsConnectionsManager.getLinkedAwsConnection(properties, project))
            .thenThrow(LinkedAwsConnNotFoundException("mock"))

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(AwsCloudConnectorConstants.CHOSEN_AWS_CONN_ID_PARAM, "No connection $CONNECTION_ID found")))
        verifyIamRole()
    }

    @Test
    @Throws(Exception::class)
    fun testProcess_ConnectionWithSessionCredentials() {
        val propertiesProcessor = createPropertiesProcessor()
        val properties = createDefaultProperties()

        ensureProjectIsProvided()
        whenever(awsConnectionsManager.getLinkedAwsConnection(properties, project))
            .thenReturn(awsConnectionBean)
        whenever(awsConnectionBean.isUsingSessionCredentials)
            .thenReturn(true)

        val invalidProperties = propertiesProcessor.process(properties)
        Assert.assertTrue(invalidProperties.contains(InvalidProperty(AwsCloudConnectorConstants.CHOSEN_AWS_CONN_ID_PARAM, "Connection must not use session credentials")))
        verifyIamRole()
    }

    private fun ensureProjectIsProvided() {
        whenever(projectManager.findProjectByExternalId(PROJECT_ID))
            .thenReturn(project)
    }

    private fun createPropertiesProcessor() = LambdaPropertiesProcessor(projectManager, awsConnectionsManager) { _, _ ->
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