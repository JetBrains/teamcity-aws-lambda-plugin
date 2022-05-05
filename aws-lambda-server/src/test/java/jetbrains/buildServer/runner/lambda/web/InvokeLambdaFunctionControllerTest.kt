package jetbrains.buildServer.runner.lambda.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.clouds.amazon.connector.featureDevelopment.AwsConnectionsManager
import jetbrains.buildServer.controllers.agent.AgentFinder
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionInvoker
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionInvokerFactory
import jetbrains.buildServer.runner.lambda.model.RunDetails
import jetbrains.buildServer.serverSide.RunningBuildsManager
import jetbrains.buildServer.serverSide.SBuildAgent
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.auth.AccessDeniedException
import org.hamcrest.Matcher
import org.jmock.AbstractExpectations
import org.jmock.Expectations
import org.jmock.States
import org.springframework.http.HttpStatus
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.Date

class InvokeLambdaFunctionControllerTest
    : JsonControllerTest<Boolean, InvokeLambdaFunctionController>(InvokeLambdaFunctionController.ALLOWED_METHODS, LambdaConstants.INVOKE_LAMBDA_PATH) {
    private lateinit var runningBuildsManager: RunningBuildsManager
    private lateinit var awsConnectionsManager: AwsConnectionsManager
    private lateinit var myLambdaFunctionInvokerFactory: LambdaFunctionInvokerFactory
    private lateinit var lambdaFunctionInvoker: LambdaFunctionInvoker
    private lateinit var runningBuild: SRunningBuild
    private lateinit var agentFinder: AgentFinder
    private lateinit var buildAgent: SBuildAgent

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
    }

    private fun mockGettingDetails(){
        m.checking(object : Expectations(){
            init {
                oneOf(request).getParameter(LambdaConstants.RUN_DETAILS)
                will(returnValue(objectMapper.writeValueAsString(listOf(RUN_DETAILS))))
            }
        })
    }

    private fun mockGettingBuildId(){
        m.checking(object : Expectations(){
            init {
                oneOf(request).getParameter(LambdaConstants.BUILD_ID)
                will(returnValue(BUILD_ID))
            }
        })
    }

    private fun mockInvokingLambdaFunction(){
        m.checking(object : Expectations(){
            init {
                oneOf(myLambdaFunctionInvokerFactory).getLambdaFunctionInvoker(getDefaultProperties(), project)
                will(returnValue(lambdaFunctionInvoker))
                oneOf(lambdaFunctionInvoker).invokeLambdaFunction(listOf(RUN_DETAILS))
            }
        })
    }

    override fun testControllerHandle() {
        mockGettingDetails()
        mockGettingBuildId()
        mockInvokingLambdaFunction()
    }

    override fun getDefaultProperties(): Map<String, String> = emptyMap()

    @Test
    override fun testCheckPermissions() {
        m.checking(object : Expectations(){
            init {
                oneOf(agentFinder).findAgent(request)
                will(returnValue(buildAgent))
            }
        })

        createController().checkPermissions(securityContext, request)
    }

    @Test(expectedExceptions = [AccessDeniedException::class])
    override fun testCheckPermissions_Failed() {
        m.checking(object : Expectations(){
            init {
                oneOf(agentFinder).findAgent(request)
                will(returnValue(null))
                oneOf(securityContext).authorityHolder
                will(returnValue(null))
            }
        })
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
            agentFinder)

    @Test
    fun testHandle_NoRunDetails() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())

        m.checking(object : Expectations(){
            init {
                oneOf(request).getParameter(LambdaConstants.RUN_DETAILS)
                will(returnValue(null))
            }
        })
        mockJsonError(HttpStatus.BAD_REQUEST)
        createController().handle(request, response)
    }

    @Test
    fun testHandle_NoBuildId() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingDetails()

        m.checking(object : Expectations(){
            init {
                oneOf(request).getParameter(LambdaConstants.BUILD_ID)
                will(returnValue(null))
            }
        })
        mockJsonError(HttpStatus.BAD_REQUEST)
        createController().handle(request, response)
    }

    @Test
    fun testHandle_FailedSerialization() {
        mockAllowedMethods()
        mockGettingBuildType()
        mockFindingBuildType()
        mockFindingProject()
        mockPropertiesBean(getDefaultProperties())
        mockGettingBuildId()

        m.checking(object : Expectations(){
            init {
                oneOf(request).getParameter(LambdaConstants.RUN_DETAILS)
                will(returnValue(BUILD_ID))
            }
        })

        val stopping = mockStopBuild()
        mockJsonError(HttpStatus.BAD_REQUEST)
        createController().handle(request, response)
        awaitMockBuild(stopping)
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

        m.checking(object : Expectations(){
            init {
                oneOf(myLambdaFunctionInvokerFactory).getLambdaFunctionInvoker(getDefaultProperties(), project)
                will(returnValue(lambdaFunctionInvoker))
                oneOf(lambdaFunctionInvoker).invokeLambdaFunction(listOf(RUN_DETAILS))
                will(throwException(Exception("mock error")))
            }
        })

        val stopping = mockStopBuild()
        mockJsonError(HttpStatus.INTERNAL_SERVER_ERROR)
        createController().handle(request, response)
        awaitMockBuild(stopping)
    }

    private fun awaitMockBuild(stopping: States) {
        synchroniser.waitUntil(stopping.`is`("finished"))
    }

    private fun mockStopBuild(): States {
        val stopping = m.states("stopping")
        m.checking(object : Expectations(){
            init {
                oneOf(runningBuildsManager).findRunningBuildById(BUILD_ID.toLong())
                will(returnValue(runningBuild))
                oneOf(runningBuild).addBuildProblem(BuildProblemData.createBuildProblem(
                        JsonControllerException::class.java.simpleName,
                        LambdaConstants.LAMBDA_INVOCATION_ERROR,
                        nonNullWithString(aNonNull(String::class.java))
                ))
                org.hamcrest.Matchers.stringContainsInOrder()

                oneOf(runningBuild).isDetachedFromAgent
                will(returnValue(true))

                oneOf(runningBuild).finish(with(any(Date::class.java)))
                then(stopping.`is`("finished"))
            }
        })
        return stopping
    }

    fun AbstractExpectations.nonNullWithString(matcher: Matcher<String>): String {
        with(matcher)
        return ""
    }

    companion object {
        val objectMapper = jacksonObjectMapper()
        const val BUILD_ID = "12345"
        val RUN_DETAILS = RunDetails(
                "username",
                "password",
                BUILD_ID,
                "serverUrl",
                "filename",
                "directoryId",
                0
        )
    }
}