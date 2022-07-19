package jetbrains.buildServer.runner.lambda.web

import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.controllers.AuthorizationInterceptor
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.SecurityContextEx
import jetbrains.buildServer.serverSide.auth.AccessChecker
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.legacy.ClassImposteriser
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import javax.servlet.ServletOutputStream
import javax.servlet.WriteListener
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private const val s = "buildTypeId"

@Listeners(MockitoTestNGListener::class)
abstract class JsonControllerTest<K : Any?, T : JsonController<K>>(private val allowedMethods: Set<String>, private val path: String) : BaseTestCase() {
    @Mock
    protected lateinit var descriptor: PluginDescriptor

    @Mock
    protected lateinit var controllerManager: WebControllerManager

    @Mock
    protected lateinit var projectManager: ProjectManager

    @Mock
    protected lateinit var accessManager: AccessChecker

    @Mock
    protected lateinit var request: HttpServletRequest

    @Mock
    protected lateinit var response: HttpServletResponse

    @Mock
    protected lateinit var buildType: SBuildType

    @Mock
    protected lateinit var project: SProject

    @Mock
    protected lateinit var authorizationInterceptor: AuthorizationInterceptor

    @Mock
    protected lateinit var securityContext: SecurityContextEx

    abstract fun createController(): T

    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()
        whenever(descriptor.getPluginResourcesPath(path))
            .thenReturn(PLUGIN_RESOURCE_PATH)
    }

    protected fun mockAllowedMethods() {
        whenever(request.method)
            .thenReturn(allowedMethods.first())
    }

    protected fun mockGettingBuildType() {
        doReturn(BUILD_TYPE_ID).`when`(
            request
        ).getParameter(LambdaConstants.BUILD_TYPE_ID)
    }

    protected fun mockFindingBuildType() {
        whenever(projectManager.findBuildTypeByExternalId(BUILD_TYPE_ID_SUFFIX))
            .thenReturn(buildType)
    }

    protected fun mockFindingProject() {
        whenever(buildType.projectId)
            .thenReturn(PROJECT_ID)
        whenever(projectManager.findProjectById(PROJECT_ID))
            .thenReturn(project)
    }

    protected fun mockPropertiesBean(properties: Map<String, String> = emptyMap()) {
        whenever(request.parameterMap)
            .thenReturn(properties.toMutableMap() as MutableMap<String, Array<String>>)
    }

    private val servletOutputStream: ServletOutputStream = object : ServletOutputStream() {
        override fun write(b: Int) {
        }

        override fun isReady(): Boolean {
            return true
        }

        override fun setWriteListener(listener: WriteListener?) {
        }

    }

    protected fun verifyWriteJson() {
        Mockito.verify(response).contentType = MediaType.APPLICATION_JSON_VALUE
    }

    protected fun mockWriteJson() {
        whenever(response.outputStream)
            .thenReturn(servletOutputStream)
    }

    protected fun verifyJsonError(httpStatus: HttpStatus) {
        Mockito.verify(response).status = httpStatus.value()
        Mockito.verify(response).contentType = MediaType.TEXT_PLAIN_VALUE
    }

    @Test
    fun testHandle() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        testControllerHandle()
        createController().handle(request, response)
        verifyControllerHandle()
    }

    @Test
    fun testHandle_notAllowOtherMethodsTest() {
        val client = createController()
        val mockMethod = "${allowedMethods.first()}-MOCK"
        whenever(request.method)
            .thenReturn(mockMethod)

        mockWriteJson()
        allowedMethods.forEach {
            client.handle(request, response)
        }
        verifyJsonError(HttpStatus.METHOD_NOT_ALLOWED)
        Mockito.verify(request, times(0)).getParameter(any())
    }

    @Test
    fun testHandle_NoBuildTypeId() {
        mockAllowedMethods()
        whenever(request.getParameter(LambdaConstants.BUILD_TYPE_ID))
            .thenReturn(null)

        mockWriteJson()
        createController().handle(request, response)
        verifyJsonError(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun testHandle_NoBuildTypeFound() {
        mockAllowedMethods()
        mockGettingBuildType()
        whenever(projectManager.findBuildTypeByExternalId(BUILD_TYPE_ID_SUFFIX))
            .thenReturn(null)
        mockWriteJson()

        createController().handle(request, response)
        verifyJsonError(HttpStatus.NOT_FOUND)
    }

    @Test
    fun testHandle_NoProjectFound() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        whenever(buildType.projectId)
            .thenReturn(PROJECT_ID)
        whenever(projectManager.findProjectById(PROJECT_ID))
            .thenReturn(null)
        mockWriteJson()

        createController().handle(request, response)
        verifyJsonError(HttpStatus.NOT_FOUND)
    }

    abstract fun testControllerHandle()

    abstract fun verifyControllerHandle()

    abstract fun getDefaultProperties(): Map<String, String>

    abstract fun testCheckPermissions()

    abstract fun testCheckPermissions_Failed()

    companion object {
        const val PLUGIN_RESOURCE_PATH = "pluginResourcePath"
        const val BUILD_TYPE_ID_SUFFIX = "buildTypeId"
        const val BUILD_TYPE_ID = "${JsonController.BUILD_TYPE_PREFIX}$BUILD_TYPE_ID_SUFFIX"
        const val PROJECT_ID = "projectId"
    }
}