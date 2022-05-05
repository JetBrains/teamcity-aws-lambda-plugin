package jetbrains.buildServer.runner.lambda

import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.runner.lambda.MockLoggerObject.mockBuildLogger
import jetbrains.buildServer.runner.lambda.cmd.CommandLinePreparer
import jetbrains.buildServer.runner.lambda.directory.ArchiveManager
import jetbrains.buildServer.runner.lambda.directory.WorkingDirectoryTransfer
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionInvoker
import jetbrains.buildServer.runner.lambda.model.RunDetails
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LambdaBuildProcessTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var context: BuildRunnerContext
    private lateinit var buildParametersMap: BuildParametersMap
    private lateinit var workingDirectoryTransfer: WorkingDirectoryTransfer
    private lateinit var workingDirectory: File
    private lateinit var commandLinePreparer: CommandLinePreparer
    private lateinit var lambdaFunctionInvoker: LambdaFunctionInvoker
    private lateinit var logger: BuildProgressLogger
    private lateinit var build: AgentRunningBuild
    private lateinit var archiveManager: ArchiveManager
    private lateinit var workingDirectoryArchive: File


    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        context = m.mock(BuildRunnerContext::class.java)
        buildParametersMap = m.mock(BuildParametersMap::class.java)
        workingDirectoryTransfer = m.mock(WorkingDirectoryTransfer::class.java)
        workingDirectory = m.mock(File::class.java, "WorkingDirectory")
        commandLinePreparer = m.mock(CommandLinePreparer::class.java)
        lambdaFunctionInvoker = m.mock(LambdaFunctionInvoker::class.java)
        logger = m.mockBuildLogger()
        build = m.mock(AgentRunningBuild::class.java)
        archiveManager = m.mock(ArchiveManager::class.java)
        workingDirectoryArchive = m.mock(File::class.java, "WorkingDirectoryArchive")


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

                allowing(context).build
                will(returnValue(build))
                allowing(build).buildId
                will(returnValue(BUILD_ID_LONG))
                allowing(build).buildTypeId
                will(returnValue(BUILD_TYPE_ID))
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

                allowing(context).workingDirectory
                will(
                        returnValue(workingDirectory)
                )
                oneOf(commandLinePreparer).writeBuildScriptContent(PROJECT_NAME, workingDirectory)
                will(returnValue(listOf(CUSTOM_SCRIPT_FILENAME)))
                oneOf(archiveManager).archiveDirectory(workingDirectory)
                will(returnValue(workingDirectoryArchive))
                oneOf(workingDirectoryTransfer).upload(UPLOAD_KEY, workingDirectoryArchive)
                will(returnValue(DIRECTORY_ID))
                oneOf(lambdaFunctionInvoker).invokeLambdaFunction(listOf(RunDetails(
                        USERNAME,
                        PASSWORD,
                        BUILD_ID,
                        URL,
                        CUSTOM_SCRIPT_FILENAME,
                        DIRECTORY_ID,
                        RUN_NUMER
                )))
                will(returnValue(false))
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
        private const val UPLOAD_KEY = "$BUILD_TYPE_ID-$BUILD_ID_LONG"
    }
}