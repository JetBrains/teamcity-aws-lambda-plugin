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
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.springframework.http.HttpStatus
import org.testng.annotations.Listeners
import org.testng.annotations.Test

@Listeners(MockitoTestNGListener::class)
class FinishLambdaControllerTest : JsonControllerTest<Nothing?, FinishLambdaController>(
    FinishLambdaController.ALLOWED_METHODS,
    LambdaConstants.FINISH_LAMBDA_PATH
) {
    @Mock
    private lateinit var runningBuildsManager: RunningBuildsManager
    @Mock
    private lateinit var awsConnectionsManager: AwsConnectionsManager
    @Mock
    private lateinit var myLambdaFunctionInvokerFactory: LambdaFunctionInvokerFactory
    @Mock
    private lateinit var lambdaFunctionInvoker: LambdaFunctionInvoker
    @Mock
    private lateinit var runningBuild: SRunningBuild
    @Mock
    private lateinit var agentFinder: AgentFinder
    @Mock
    private lateinit var buildAgent: SBuildAgent
    @Mock
    private lateinit var buildPromotion: BuildPromotionEx

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
        doReturn(BUILD_ID).`when`(request).getParameter(LambdaConstants.BUILD_ID)
    }

    private fun mockGettingInvocationId() {
        doReturn(INVOCATION_ID_1).`when`(request).getParameter(LambdaConstants.INVOCATION_ID)
    }

    private fun mockGettingBuildPromotionAttribute(numInvocations: Int) {
        whenever(runningBuild.buildPromotion)
            .thenReturn(buildPromotion)
        whenever(buildPromotion.getAttribute(LambdaConstants.NUM_INVOCATIONS_PARAM))
            .thenReturn(numInvocations.toLong())
    }

    private fun mockFindRunningBuild() {
        whenever(runningBuildsManager.findRunningBuildById(BUILD_ID.toLong()))
            .thenReturn(runningBuild)
    }

    private fun mockFinishingBuild() {
        Mockito.verify(runningBuild).finish(any())
    }

    override fun testControllerHandle() {
        mockGettingBuildId()
        mockGettingInvocationId()
        mockGettingBuildPromotionAttribute(1)
        mockFindRunningBuild()
    }

    override fun verifyControllerHandle() {
        mockFinishingBuild()
    }

    @Test
    fun testHandle_NoBuildId() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        doReturn(null).`when`(request).getParameter(LambdaConstants.BUILD_ID)

        mockWriteJson()
        createController().handle(request, response)
        verifyJsonError(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun testHandle_NoInvocationId() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingBuildId()

        doReturn(null).`when`(request).getParameter(LambdaConstants.INVOCATION_ID)

        mockWriteJson()
        createController().handle(request, response)
        verifyJsonError(HttpStatus.BAD_REQUEST)
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

        whenever(runningBuildsManager.findRunningBuildById(BUILD_ID.toLong()))
            .thenReturn(null)
        mockWriteJson()
        createController().handle(request, response)
        verifyJsonError(HttpStatus.BAD_REQUEST)
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

        whenever(runningBuild.buildPromotion)
            .thenReturn(buildPromotion)
        whenever(buildPromotion.getAttribute(LambdaConstants.NUM_INVOCATIONS_PARAM))
            .thenReturn(null)

        mockWriteJson()
        createController().handle(request, response)
        verifyJsonError(HttpStatus.INTERNAL_SERVER_ERROR)
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

        doReturn(BUILD_ID).`when`(request).getParameter(LambdaConstants.INVOCATION_ID)

        controller.handle(request, response)
        mockFinishingBuild()
    }

    override fun getDefaultProperties(): Map<String, String> = emptyMap()

    @Test
    override fun testCheckPermissions() {
        whenever(agentFinder.findAgent(request))
            .thenReturn(buildAgent)

        createController().checkPermissions(securityContext, request)
    }

    override fun testCheckPermissions_Failed() {
        whenever(agentFinder.findAgent(request))
            .thenReturn(null)
        whenever(securityContext.authorityHolder)
            .thenReturn(null)

        createController().checkPermissions(securityContext, request)
    }

    companion object {
        const val BUILD_ID = "12345"
        const val INVOCATION_ID_1 = "123456"
        const val INVOCATION_ID_2 = "123457"
    }

}