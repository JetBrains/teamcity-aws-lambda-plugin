package jetbrains.buildServer.runner.lambda.web

import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.controllers.AuthorizationInterceptor
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.SecurityContextEx
import jetbrains.buildServer.serverSide.auth.AccessChecker
import jetbrains.buildServer.serverSide.auth.SecurityContext
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import javax.servlet.ServletOutputStream
import javax.servlet.WriteListener
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private const val s = "buildTypeId"

abstract class JsonControllerTest<K : Any, T : JsonController<K>>(private val allowedMethods: Set<String>, private val path: String) : BaseTestCase() {
    protected lateinit var m: Mockery
    protected lateinit var descriptor: PluginDescriptor
    protected lateinit var controllerManager: WebControllerManager
    protected lateinit var projectManager: ProjectManager
    protected lateinit var accessManager: AccessChecker
    protected lateinit var request: HttpServletRequest
    protected lateinit var response: HttpServletResponse
    protected lateinit var buildType: SBuildType
    protected lateinit var project: SProject
    protected lateinit var authorizationInterceptor: AuthorizationInterceptor
    protected lateinit var securityContext: SecurityContextEx
    protected val synchroniser = Synchroniser()

    abstract fun createController(): T

    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }

    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(synchroniser)
        descriptor = m.mock(PluginDescriptor::class.java)
        controllerManager = m.mock(WebControllerManager::class.java)
        projectManager = m.mock(ProjectManager::class.java)
        accessManager = m.mock(AccessChecker::class.java)
        request = m.mock(HttpServletRequest::class.java)
        response = m.mock(HttpServletResponse::class.java)
        buildType = m.mock(SBuildType::class.java)
        project = m.mock(SProject::class.java)
        authorizationInterceptor = m.mock(AuthorizationInterceptor::class.java)
        securityContext = m.mock(SecurityContextEx::class.java)

        m.checking(object : Expectations() {
            init {
                oneOf(descriptor).getPluginResourcesPath(path)
                will(returnValue(PLUGIN_RESOURCE_PATH))
                oneOf(authorizationInterceptor)
                        .addPathBasedPermissionsChecker(
                                with(PLUGIN_RESOURCE_PATH),
                                with(any(JsonController::class.java)))
                oneOf(controllerManager)
                        .registerController(
                                with(PLUGIN_RESOURCE_PATH),
                                with(any(JsonController::class.java)))
            }
        })
    }

    protected fun mockAllowedMethods() {
        m.checking(object : Expectations() {
            init {
                oneOf(request).method
                will(returnValue(allowedMethods.first()))
            }
        })
    }

    protected fun mockGettingBuildType() {
        m.checking(object : Expectations() {
            init {
                oneOf(request).getParameter(LambdaConstants.BUILD_TYPE_ID)
                will(returnValue(BUILD_TYPE_ID))
            }
        })
    }

    protected fun mockFindingBuildType() {
        m.checking(object : Expectations() {
            init {
                oneOf(projectManager).findBuildTypeByExternalId(BUILD_TYPE_ID_SUFFIX)
                will(returnValue(buildType))
            }
        })
    }

    protected fun mockFindingProject() {
        m.checking(object : Expectations() {
            init {
                oneOf(buildType).projectId
                will(returnValue(PROJECT_ID))
                oneOf(projectManager).findProjectById(PROJECT_ID)
                will(returnValue(project))
            }
        })
    }

    protected fun mockPermissionsChecker() {
        m.checking(object : Expectations() {
            init {
                oneOf(accessManager).checkCanEditProject(project)
            }
        })
    }

    protected fun mockPropertiesBean(properties: Map<String, String> = emptyMap()) {
        m.checking(object : Expectations() {
            init {
                oneOf(request).parameterMap
                will(returnValue(properties))
            }
        })
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

    protected fun mockWriteJson() {
        m.checking(object : Expectations() {
            init {
                oneOf(response).outputStream
                will(returnValue(servletOutputStream))
                oneOf(response).contentType = MediaType.APPLICATION_JSON_VALUE
            }
        })
    }

    protected fun mockJsonError(httpStatus: HttpStatus) {
        m.checking(object : Expectations() {
            init {
                oneOf(response).outputStream
                will(returnValue(servletOutputStream))
                oneOf(response).status = httpStatus.value()
                oneOf(response).contentType = MediaType.TEXT_PLAIN_VALUE
            }
        })
    }

    @Test
    fun testHandle() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockWriteJson()
        testControllerHandle()
        createController().handle(request, response)
    }

    @Test
    fun testHandle_notAllowOtherMethodsTest() {
        val client = createController()

        m.checking(object : Expectations() {
            init {
                val mockMethod = "${allowedMethods.first()}-MOCK"
                oneOf(request).method
                will(returnValue(mockMethod))
                never(request).getParameter(with(any(String::class.java)))
            }
        })

        mockJsonError(HttpStatus.METHOD_NOT_ALLOWED)

        allowedMethods.forEach {
            client.handle(request, response)
        }
    }

    @Test
    fun testHandle_NoBuildTypeId() {
        mockAllowedMethods()
        m.checking(object : Expectations() {
            init {
                oneOf(request).getParameter(LambdaConstants.BUILD_TYPE_ID)
                will(returnValue(null))
            }
        })
        mockJsonError(HttpStatus.BAD_REQUEST)
        createController().handle(request, response)
    }

    @Test
    fun testHandle_NoBuildTypeFound() {
        mockAllowedMethods()
        mockGettingBuildType()
        m.checking(object : Expectations() {
            init {
                oneOf(projectManager).findBuildTypeByExternalId(BUILD_TYPE_ID_SUFFIX)
                will(returnValue(null))
            }
        })
        mockJsonError(HttpStatus.NOT_FOUND)
        createController().handle(request, response)
    }

    @Test
    fun testHandle_NoProjectFound() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        m.checking(object : Expectations() {
            init {
                oneOf(buildType).projectId
                will(returnValue(PROJECT_ID))
                oneOf(projectManager).findProjectById(PROJECT_ID)
                will(returnValue(null))
            }
        })
        mockJsonError(HttpStatus.NOT_FOUND)
        createController().handle(request, response)
    }

    abstract fun testControllerHandle()

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