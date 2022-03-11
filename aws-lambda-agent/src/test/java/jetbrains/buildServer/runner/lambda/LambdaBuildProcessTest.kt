package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.AWSLambdaAsync
import com.amazonaws.services.lambda.model.InvokeRequest
import com.fasterxml.jackson.databind.ObjectMapper
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.BuildFinishedStatus
import jetbrains.buildServer.agent.BuildParametersMap
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.cmd.CommandLinePreparer
import jetbrains.buildServer.runner.lambda.directory.WorkingDirectoryTransfer
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.api.Invocation
import org.jmock.lib.action.CustomAction
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File

class LambdaBuildProcessTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var context: BuildRunnerContext
    private lateinit var awsLambda: AWSLambdaAsync
    private lateinit var objectMapper: ObjectMapper
    private lateinit var buildParametersMap: BuildParametersMap
    private lateinit var workingDirectoryTransfer: WorkingDirectoryTransfer
    private lateinit var workingDirectory: File
    private lateinit var commandLinePreparer: CommandLinePreparer


    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        context = m.mock(BuildRunnerContext::class.java)
        awsLambda = m.mock(AWSLambdaAsync::class.java)
        objectMapper = m.mock(ObjectMapper::class.java)
        buildParametersMap = m.mock(BuildParametersMap::class.java)
        workingDirectoryTransfer = m.mock(WorkingDirectoryTransfer::class.java)
        workingDirectory = m.mock(File::class.java)
        commandLinePreparer = m.mock(CommandLinePreparer::class.java)

        m.checking(object : Expectations() {
            init {
                allowing(context).buildParameters
                will(returnValue(buildParametersMap))
                allowing(buildParametersMap).allParameters
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.USERNAME_SYSTEM_PROPERTY, USERNAME),
                            Pair(LambdaConstants.PASSWORD_SYSTEM_PROPERTY, PASSWORD),
                        )
                    )
                )
            }
        })
    }

    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }

    @Test
    @Throws(Exception::class)
    fun testWaitFor() {
        val buildProcess = createBuildProcess()

        m.checking(object : Expectations() {
            init {
                allowing(context).configParameters
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.TEAMCITY_BUILD_ID, BUILD_ID),
                            Pair(LambdaConstants.TEAMCITY_SERVER_URL, URL),
                        )
                    )
                )

                allowing(buildParametersMap).systemProperties
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.TEAMCITY_PROJECT_NAME, PROJECT_NAME)
                        )
                    )
                )
                allowing(buildParametersMap).environmentVariables
                will(returnValue(ENV_PARAMS))

                allowing(context).workingDirectory
                will(
                    returnValue(workingDirectory)
                )
                oneOf(commandLinePreparer).writeBuildScriptContent(PROJECT_NAME, workingDirectory)
                will(returnValue(CUSTOM_SCRIPT_FILENAME))
                oneOf(workingDirectoryTransfer).upload(workingDirectory)
                will(returnValue(DIRECTORY_ID))
                allowing(objectMapper).writeValueAsString(
                    RunDetails(
                        USERNAME,
                        PASSWORD,
                        BUILD_ID,
                        URL,
                        ENV_PARAMS,
                        CUSTOM_SCRIPT_FILENAME,
                        DIRECTORY_ID
                    )
                )
                will(returnValue(OBJECT_STRING))

                oneOf(awsLambda).invokeAsync(
                    InvokeRequest().withFunctionName(LambdaConstants.FUNCTION_NAME).withPayload(
                        OBJECT_STRING
                    )
                )
            }
        })

        val status = buildProcess.waitFor()
        Assert.assertEquals(status, BuildFinishedStatus.FINISHED_DETACHED)
        Assert.assertEquals(buildProcess.waitFor(), BuildFinishedStatus.FINISHED_DETACHED)
        Assert.assertTrue(buildProcess.isFinished)
        Assert.assertFalse(buildProcess.isInterrupted)
    }

    @Test
    @Throws(Exception::class)
    fun testWaitFor_LocalhostUrl() {
        val buildProcess = createBuildProcess()

        m.checking(object : Expectations() {
            init {
                allowing(context).configParameters
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.TEAMCITY_BUILD_ID, BUILD_ID),
                            Pair(LambdaConstants.TEAMCITY_SERVER_URL, LOCALHOST),
                        )
                    )
                )

                allowing(buildParametersMap).systemProperties
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.TEAMCITY_PROJECT_NAME, PROJECT_NAME)
                        )
                    )
                )
                allowing(buildParametersMap).environmentVariables
                will(returnValue(ENV_PARAMS))

                allowing(context).workingDirectory
                will(
                    returnValue(workingDirectory)
                )
                oneOf(commandLinePreparer).writeBuildScriptContent(PROJECT_NAME, workingDirectory)
                will(returnValue(CUSTOM_SCRIPT_FILENAME))
                oneOf(workingDirectoryTransfer).upload(workingDirectory)
                will(returnValue(DIRECTORY_ID))

                allowing(objectMapper).writeValueAsString(
                    RunDetails(
                        USERNAME,
                        PASSWORD,
                        BUILD_ID,
                        "172.17.0.1",
                        ENV_PARAMS,
                        CUSTOM_SCRIPT_FILENAME,
                        DIRECTORY_ID
                    )
                )
                will(returnValue(OBJECT_STRING))

                oneOf(awsLambda).invokeAsync(
                    InvokeRequest().withFunctionName(LambdaConstants.FUNCTION_NAME).withPayload(
                        OBJECT_STRING
                    )
                )
            }
        })

        val status = buildProcess.waitFor()
        Assert.assertEquals(status, BuildFinishedStatus.FINISHED_DETACHED)
        Assert.assertEquals(buildProcess.waitFor(), BuildFinishedStatus.FINISHED_DETACHED)
        Assert.assertTrue(buildProcess.isFinished)
        Assert.assertFalse(buildProcess.isInterrupted)
    }

    @Test
    @Throws(Exception::class)
    fun testWaitFor_MidwayInterruptedBuild() {
        val buildProcess = createBuildProcess()

        m.checking(object : Expectations() {
            init {
                allowing(context).configParameters
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.TEAMCITY_BUILD_ID, BUILD_ID),
                            Pair(LambdaConstants.TEAMCITY_SERVER_URL, URL),
                        )
                    )
                )

                allowing(buildParametersMap).systemProperties
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.TEAMCITY_PROJECT_NAME, PROJECT_NAME)
                        )
                    )
                )
                allowing(buildParametersMap).environmentVariables
                will(returnValue(ENV_PARAMS))

                allowing(context).workingDirectory
                will(
                    returnValue(workingDirectory)
                )
                oneOf(commandLinePreparer).writeBuildScriptContent(PROJECT_NAME, workingDirectory)
                will(returnValue(CUSTOM_SCRIPT_FILENAME))
                oneOf(workingDirectoryTransfer).upload(workingDirectory)
                will(returnValue(DIRECTORY_ID))

                allowing(objectMapper).writeValueAsString(
                    RunDetails(
                        USERNAME,
                        PASSWORD,
                        BUILD_ID,
                        URL,
                        ENV_PARAMS,
                        CUSTOM_SCRIPT_FILENAME,
                        DIRECTORY_ID
                    )
                )
                will(object : CustomAction("Interrupts process") {
                    override fun invoke(invocation: Invocation): Any {
                        buildProcess.interrupt()
                        return OBJECT_STRING
                    }
                })

                never(awsLambda).invokeAsync(
                    InvokeRequest().withFunctionName(LambdaConstants.FUNCTION_NAME).withPayload(
                        OBJECT_STRING
                    )
                )
            }
        })

        val status = buildProcess.waitFor()
        Assert.assertEquals(status, BuildFinishedStatus.INTERRUPTED)
        Assert.assertEquals(buildProcess.waitFor(), BuildFinishedStatus.INTERRUPTED)
        Assert.assertTrue(buildProcess.isInterrupted)
        Assert.assertFalse(buildProcess.isFinished)
    }

    @Test
    @Throws(Exception::class)
    fun testWaitFor_InterruptedBuild() {
        val buildProcess = createBuildProcess()
        buildProcess.interrupt()

        val status = buildProcess.waitFor()
        Assert.assertEquals(status, BuildFinishedStatus.INTERRUPTED)
        Assert.assertEquals(buildProcess.waitFor(), BuildFinishedStatus.INTERRUPTED)
        Assert.assertTrue(buildProcess.isInterrupted)
        Assert.assertFalse(buildProcess.isFinished)
    }

    private fun createBuildProcess() = LambdaBuildProcess(
        context,
        awsLambda,
        objectMapper,
        workingDirectoryTransfer,
        commandLinePreparer
    )

    companion object {
        private const val OBJECT_STRING = "objectString"
        private const val USERNAME = "username"
        private const val PASSWORD = "password"
        private const val URL = "url"
        private const val LOCALHOST = "localhost"
        private const val BUILD_ID = "buildId"
        private val ENV_PARAMS = mapOf(Pair("key", "value"))
        private const val CUSTOM_SCRIPT_FILENAME = "customScriptFilename"
        private const val DIRECTORY_ID = "directoryId"
        private const val PROJECT_NAME = "projectName"
    }
}