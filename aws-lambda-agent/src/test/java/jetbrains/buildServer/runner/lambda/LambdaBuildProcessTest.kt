package jetbrains.buildServer.runner.lambda

import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.runner.lambda.cmd.CommandLinePreparer
import jetbrains.buildServer.runner.lambda.directory.ArchiveManager
import jetbrains.buildServer.runner.lambda.directory.WorkingDirectoryTransfer
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionInvoker
import jetbrains.buildServer.runner.lambda.model.BuildDetails
import jetbrains.buildServer.runner.lambda.model.RunDetails
import org.jmock.Mockery
import org.jmock.lib.legacy.ClassImposteriser
import org.mockito.Mock
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Listeners(MockitoTestNGListener::class)
class LambdaBuildProcessTest : BaseTestCase() {
    @Mock
    private lateinit var context: BuildRunnerContext
    @Mock
    private lateinit var buildParametersMap: BuildParametersMap
    @Mock
    private lateinit var workingDirectoryTransfer: WorkingDirectoryTransfer
    @Mock
    private lateinit var workingDirectory: File
    @Mock
    private lateinit var commandLinePreparer: CommandLinePreparer
    @Mock
    private lateinit var lambdaFunctionInvoker: LambdaFunctionInvoker
    @Mock
    private lateinit var logger: BuildProgressLogger
    @Mock
    private lateinit var build: AgentRunningBuild
    @Mock
    private lateinit var archiveManager: ArchiveManager
    @Mock
    private lateinit var workingDirectoryArchive: File
    @Mock
    private lateinit var buildAgentConfiguration: BuildAgentConfiguration

    @Test
    @Throws(Exception::class)
    fun testWaitFor() {
        val buildProcess = createBuildProcess()
        whenever(context.buildParameters).thenReturn(buildParametersMap)
        whenever(buildParametersMap.allParameters).thenReturn(
            mapOf(
                Pair(LambdaConstants.USERNAME_SYSTEM_PROPERTY, USERNAME),
                Pair(LambdaConstants.PASSWORD_SYSTEM_PROPERTY, PASSWORD),
                Pair(LambdaConstants.BUILD_TYPE_SYSTEM_PROPERTY, BUILD_TYPE_ID)
            )
        )
        whenever(context.build).thenReturn(build)
        whenever(build.buildId).thenReturn(BUILD_ID_LONG)
        whenever(build.buildTypeId).thenReturn(BUILD_TYPE_ID)
        whenever(build.agentConfiguration).thenReturn(buildAgentConfiguration)
        whenever(buildAgentConfiguration.name).thenReturn(AGENT_NAME)

        whenever(context.configParameters).thenReturn(
            mapOf(
                Pair(LambdaConstants.TEAMCITY_BUILD_ID, BUILD_ID),
                Pair(LambdaConstants.TEAMCITY_SERVER_URL, URL),
            )
        )
        whenever(buildParametersMap.systemProperties).thenReturn(
            mapOf(
                Pair(LambdaConstants.TEAMCITY_PROJECT_NAME, PROJECT_NAME)
            )
        )
        whenever(context.workingDirectory).thenReturn(workingDirectory)
        whenever(commandLinePreparer.writeBuildScriptContent(PROJECT_NAME, workingDirectory)).thenReturn(listOf(CUSTOM_SCRIPT_FILENAME))
        whenever(archiveManager.archiveDirectory(workingDirectory)).thenReturn(workingDirectoryArchive)
        whenever(workingDirectoryTransfer.upload(UPLOAD_KEY, workingDirectoryArchive)).thenReturn(DIRECTORY_ID)
        whenever(
            lambdaFunctionInvoker.invokeLambdaFunction(
                listOf(
                    RunDetails(
                        USERNAME,
                        PASSWORD,
                        URL,
                        CUSTOM_SCRIPT_FILENAME,
                        DIRECTORY_ID,
                        RUN_NUMER,
                        BuildDetails(
                            BUILD_ID,
                            BUILD_TYPE_ID,
                            AGENT_NAME
                        )
                    )
                )
            )
        ).thenReturn(false)

        val status = buildProcess.waitFor()
        Assert.assertEquals(status, BuildFinishedStatus.FINISHED_DETACHED)
        Assert.assertEquals(buildProcess.waitFor(), BuildFinishedStatus.FINISHED_DETACHED)
        Assert.assertTrue(buildProcess.isFinished)
        Assert.assertFalse(buildProcess.isInterrupted)
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
        logger,
        workingDirectoryTransfer,
        commandLinePreparer,
        archiveManager,
        lambdaFunctionInvoker,
        AtomicBoolean()
    )

    companion object {
        private const val USERNAME = "username"
        private const val PASSWORD = "password"
        private const val URL = "url"
        private const val BUILD_ID = "buildId"
        private const val CUSTOM_SCRIPT_FILENAME = "customScriptFilename"
        private const val DIRECTORY_ID = "directoryId"
        private const val RUN_NUMER = 0
        private const val PROJECT_NAME = "projectName"
        private const val FUNCTION_NAME = "functionName"
        private const val BUILD_ID_LONG = 1234L
        private const val BUILD_TYPE_ID = "buildTypeId"
        private const val AGENT_NAME = "agentName"
        private const val UPLOAD_KEY = "$BUILD_TYPE_ID-$BUILD_ID_LONG"
    }
}