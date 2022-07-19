package jetbrains.buildServer.runner.lambda.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.clouds.amazon.connector.featureDevelopment.AwsConnectionsManager
import jetbrains.buildServer.controllers.agent.AgentFinder
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionInvoker
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionInvokerFactory
import jetbrains.buildServer.runner.lambda.model.BuildDetails
import jetbrains.buildServer.runner.lambda.model.RunDetails
import jetbrains.buildServer.serverSide.BuildPromotionEx
import jetbrains.buildServer.serverSide.RunningBuildsManager
import jetbrains.buildServer.serverSide.SBuildAgent
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.auth.AccessDeniedException
import org.hamcrest.Matcher
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.springframework.http.HttpStatus
import org.testng.Assert
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Listeners(MockitoTestNGListener::class)
class InvokeLambdaFunctionControllerTest
    : JsonControllerTest<Boolean, InvokeLambdaFunctionController>(InvokeLambdaFunctionController.ALLOWED_METHODS, LambdaConstants.INVOKE_LAMBDA_PATH) {
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

    private fun mockGettingDetails() {
        doReturn(objectMapper.writeValueAsString(listOf(RUN_DETAILS)))
            .`when`(request).getParameter(LambdaConstants.RUN_DETAILS)
    }

    private fun mockGettingBuildId() {
        doReturn(BUILD_ID)
            .`when`(request).getParameter(LambdaConstants.BUILD_ID)
    }

    private fun verifyInvokingLambdaFunction() {
        Mockito.verify(lambdaFunctionInvoker)
            .invokeLambdaFunction(listOf(RUN_DETAILS))
    }

    private fun mockInvokingLambdaFunction() {
        whenever(myLambdaFunctionInvokerFactory.getLambdaFunctionInvoker(getDefaultProperties(), project))
            .thenReturn(lambdaFunctionInvoker)
    }

    private fun verifyStoringBuildPromotionAttribute() {
        Mockito.verify(buildPromotion).setAttribute(LambdaConstants.NUM_INVOCATIONS_PARAM, 1)
        Mockito.verify(buildPromotion).persist()
    }

    private fun mockStoringBuildPromotionAttribute() {
        whenever(runningBuild.buildPromotion)
            .thenReturn(buildPromotion)
    }

    override fun testControllerHandle() {
        mockGettingDetails()
        mockGettingBuildId()
        mockWriteJson()
        mockInvokingLambdaFunction()
        mockFindRunningBuild()
        mockStoringBuildPromotionAttribute()
    }

    override fun verifyControllerHandle() {
        verifyWriteJson()
        verifyInvokingLambdaFunction()
        verifyStoringBuildPromotionAttribute()
    }

    override fun getDefaultProperties(): Map<String, String> = emptyMap()

    @Test
    override fun testCheckPermissions() {
        whenever(agentFinder.findAgent(request))
            .thenReturn(buildAgent)

        createController().checkPermissions(securityContext, request)
    }

    @Test(expectedExceptions = [AccessDeniedException::class])
    override fun testCheckPermissions_Failed() {
        whenever(agentFinder.findAgent(request))
            .thenReturn(null)
        whenever(securityContext.authorityHolder)
            .thenReturn(null)

        createController().checkPermissions(securityContext, request)
    }

    override fun createController() = InvokeLambdaFunctionController(
        descriptor,
        controllerManager,
        projectManager,
        accessManager,
        authorizationInterceptor,
        runningBuildsManager,
        myLambdaFunctionInvokerFactory,
        agentFinder
    )

    @Test
    fun testHandle_NoRunDetails() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())

        doReturn(null)
            .`when`(request).getParameter(LambdaConstants.RUN_DETAILS)
        mockWriteJson()
        createController().handle(request, response)
        verifyJsonError(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun testHandle_NoBuildId() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingDetails()

        doReturn(null)
            .`when`(request).getParameter(LambdaConstants.BUILD_ID)
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
        mockGettingDetails()

        whenever(runningBuildsManager.findRunningBuildById(BUILD_ID.toLong()))
            .thenReturn(null)

        mockWriteJson()
        createController().handle(request, response)
        verifyJsonError(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun testHandle_FailedSerialization() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingBuildId()
        mockFindRunningBuild()

        doReturn(BUILD_ID).`when`(request).getParameter(LambdaConstants.RUN_DETAILS)

        mockWriteJson()
        val latch = mockStopBuild()
        createController().handle(request, response)
        awaitMockBuild(latch)
        verifyJsonError(HttpStatus.BAD_REQUEST)
        verifyStopBuild()
    }

    @Test
    fun testHandle_FailedInvocation() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingDetails()
        mockGettingBuildId()
        mockFindRunningBuild()
        mockStoringBuildPromotionAttribute()

        whenever(myLambdaFunctionInvokerFactory.getLambdaFunctionInvoker(getDefaultProperties(), project))
            .thenReturn(lambdaFunctionInvoker)
        whenever(lambdaFunctionInvoker.invokeLambdaFunction(listOf(RUN_DETAILS)))
            .thenThrow(Exception("mock error"))

        mockWriteJson()
        val latch = mockStopBuild()
        createController().handle(request, response)
        awaitMockBuild(latch)
        verifyJsonError(HttpStatus.INTERNAL_SERVER_ERROR)
        verifyStoringBuildPromotionAttribute()
        verifyStopBuild()
    }


    private fun awaitMockBuild(latch: CountDownLatch) {
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS))
    }

    private fun mockFindRunningBuild() {
        whenever(runningBuildsManager.findRunningBuildById(BUILD_ID.toLong()))
            .thenReturn(runningBuild)
    }

    private fun verifyStopBuild() {
        Mockito.verify(runningBuild).addBuildProblem(any())
    }

    private fun mockStopBuild(): CountDownLatch {
        val latch = CountDownLatch(1)
        whenever(runningBuild.isDetachedFromAgent)
            .thenReturn(true)
        whenever(runningBuild.finish(any()))
            .thenAnswer {
                latch.countDown()
            }

        return latch
    }

    companion object {
        val objectMapper = jacksonObjectMapper()
        const val BUILD_ID = "12345"
        val RUN_DETAILS = RunDetails(
            "username",
            "password",
            "serverUrl",
            "filename",
            "directoryId",
            0,
            BuildDetails(
                BUILD_ID,
                "buildTypeId",
                "agentName"
            )
        )
    }
}