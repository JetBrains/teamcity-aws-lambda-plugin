package jetbrains.buildServer.runner.lambda.web

import jetbrains.buildServer.clouds.amazon.connector.featureDevelopment.AwsConnectionsManager
import jetbrains.buildServer.controllers.agent.AgentFinder
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionInvoker
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionInvokerFactory
import jetbrains.buildServer.serverSide.BuildPromotionEx
import jetbrains.buildServer.serverSide.RunningBuildsManager
import jetbrains.buildServer.serverSide.SBuildAgent
import jetbrains.buildServer.serverSide.SRunningBuild
import org.jmock.Expectations
import org.springframework.http.HttpStatus
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.Date

class FinishLambdaControllerTest : JsonControllerTest<Nothing?, FinishLambdaController>(
        FinishLambdaController.ALLOWED_METHODS,
        LambdaConstants.FINISH_LAMBDA_PATH
) {
    private lateinit var runningBuildsManager: RunningBuildsManager
    private lateinit var awsConnectionsManager: AwsConnectionsManager
    private lateinit var myLambdaFunctionInvokerFactory: LambdaFunctionInvokerFactory
    private lateinit var lambdaFunctionInvoker: LambdaFunctionInvoker
    private lateinit var runningBuild: SRunningBuild
    private lateinit var agentFinder: AgentFinder
    private lateinit var buildAgent: SBuildAgent
    private lateinit var buildPromotion: BuildPromotionEx

    @BeforeMethod
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        runningBuildsManager = m.mock(RunningBuildsManager::class.java)
        awsConnectionsManager = m.mock(AwsConnectionsManager::class.java)
        myLambdaFunctionInvokerFactory = m.mock(LambdaFunctionInvokerFactory::class.java)
        lambdaFunctionInvoker = m.mock(LambdaFunctionInvoker::class.java)
        runningBuild = m.mock(SRunningBuild::class.java)
        agentFinder = m.mock(AgentFinder::class.java)
        buildAgent = m.mock(SBuildAgent::class.java)
        buildPromotion = m.mock(BuildPromotionEx::class.java)
    }

    override fun createController(): FinishLambdaController =
            FinishLambdaController(
                    descriptor,
                    controllerManager,
                    projectManager,
                    accessManager,
                    authorizationInterceptor,
                    runningBuildsManager,
                    agentFinder
            )

    private fun mockGettingBuildId() {
        m.checking(object : Expectations() {
            init {
                oneOf(request).getParameter(LambdaConstants.BUILD_ID)
                will(returnValue(BUILD_ID))
            }
        })
    }

    private fun mockGettingInvocationId() {
        m.checking(object : Expectations() {
            init {
                oneOf(request).getParameter(LambdaConstants.INVOCATION_ID)
                will(returnValue(INVOCATION_ID_1))
            }
        })
    }

    private fun mockGettingBuildPromotionAttribute(numInvocations: Int) {

        m.checking(object : Expectations() {
            init {
                oneOf(runningBuild).buildPromotion
                will(returnValue(buildPromotion))
                oneOf(buildPromotion).getAttribute(LambdaConstants.NUM_INVOCATIONS_PARAM)
                will(returnValue(numInvocations.toLong()))
            }
        })
    }

    private fun mockFindRunningBuild() {
        m.checking(object : Expectations() {
            init {
                allowing(runningBuildsManager).findRunningBuildById(BUILD_ID.toLong())
                will(returnValue(runningBuild))
            }
        })
    }

    private fun mockFinishingBuild(){
        m.checking(object : Expectations(){
            init {
                oneOf(runningBuild).finish(with(any(Date::class.java)))
            }
        })
    }

    override fun testControllerHandle() {
        mockGettingBuildId()
        mockGettingInvocationId()
        mockGettingBuildPromotionAttribute(1)
        mockFindRunningBuild()
        mockFinishingBuild()
    }

    @Test
    fun testHandle_NoBuildId() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        m.checking(object : Expectations() {
            init {
                oneOf(request).getParameter(LambdaConstants.BUILD_ID)
                will(returnValue(null))
            }
        })
        mockJsonError(HttpStatus.BAD_REQUEST)
        createController().handle(request, response)
    }

    @Test
    fun testHandle_NoInvocationId() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingBuildId()

        m.checking(object : Expectations() {
            init {
                oneOf(request).getParameter(LambdaConstants.INVOCATION_ID)
                will(returnValue(null))
            }
        })
        mockJsonError(HttpStatus.BAD_REQUEST)
        createController().handle(request, response)
    }

    @Test
    fun testHandle_NoRunningBuild() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingBuildId()
        mockGettingInvocationId()

        m.checking(object : Expectations() {
            init {
                oneOf(runningBuildsManager).findRunningBuildById(BUILD_ID.toLong())
                will(returnValue(null))
            }
        })
        mockJsonError(HttpStatus.BAD_REQUEST)
        createController().handle(request, response)
    }

    @Test
    fun testHandle_NoInvocationAttribute() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingBuildId()
        mockGettingInvocationId()
        mockFindRunningBuild()

        m.checking(object : Expectations() {
            init {
                oneOf(runningBuild).buildPromotion
                will(returnValue(buildPromotion))
                oneOf(buildPromotion).getAttribute(LambdaConstants.NUM_INVOCATIONS_PARAM)
                will(returnValue(null))
            }
        })
        mockJsonError(HttpStatus.INTERNAL_SERVER_ERROR)
        createController().handle(request, response)
    }

    @Test
    fun testHandle_MultipleInvocations() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingBuildId()
        mockGettingInvocationId()
        mockFindRunningBuild()
        mockGettingBuildPromotionAttribute(2)
        val controller = createController()
        controller.handle(request, response)

        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingBuildId()

        m.checking(object : Expectations() {
            init {
                oneOf(request).getParameter(LambdaConstants.INVOCATION_ID)
                will(returnValue(INVOCATION_ID_2))
            }
        })
        mockFinishingBuild()
        controller.handle(request, response)
    }

    override fun getDefaultProperties(): Map<String, String> = emptyMap()

    @Test
    override fun testCheckPermissions() {
        m.checking(object : Expectations() {
            init {
                oneOf(agentFinder).findAgent(request)
                will(returnValue(buildAgent))
            }
        })

        createController().checkPermissions(securityContext, request)
    }

    override fun testCheckPermissions_Failed() {
        m.checking(object : Expectations() {
            init {
                oneOf(agentFinder).findAgent(request)
                will(returnValue(null))
                oneOf(securityContext).authorityHolder
                will(returnValue(null))
            }
        })
        createController().checkPermissions(securityContext, request)
    }

    companion object {
        const val BUILD_ID = "12345"
        const val INVOCATION_ID_1 = "123456"
        const val INVOCATION_ID_2 = "123457"
    }

}