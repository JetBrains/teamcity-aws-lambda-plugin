package jetbrains.buildServer.runner.lambda.cmd

import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.MockLoggerObject.mockBuildLogger
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class UnixCommandLinePreparerTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var context: BuildRunnerContext
    private lateinit var logger: BuildProgressLogger

    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        context = m.mock(BuildRunnerContext::class.java)
        logger = m.mockBuildLogger()

        m.checking(object : Expectations() {
            init {
                oneOf(context).runnerParameters
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.SCRIPT_CONTENT_PARAM, SCRIPT_CONTENT)
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
    fun testWriteBuildScriptContent() {
        val unixCommandLinePreparer = createClient()
        val tempDir = createTempDirectory().toFile()

        val filename = unixCommandLinePreparer.writeBuildScriptContent(PROJECT_NAME, tempDir)

        Assert.assertEquals(filename, LambdaConstants.SCRIPT_CONTENT_FILENAME)

        val scriptFile = File("${tempDir.absolutePath}/$filename")
        Assert.assertTrue(scriptFile.exists())

        val content = String(Files.readAllBytes(scriptFile.toPath()))
        Assert.assertEquals(
            content,
            LambdaConstants.SCRIPT_CONTENT_HEADER + LambdaConstants.SCRIPT_CONTENT_CHANGE_DIRECTORY_PREFIX + SCRIPT_CONTENT
        )
    }

    @Test
    fun testWriteBuildScriptContent_ExistingFile() {
        val unixCommandLinePreparer = createClient()
        val tempDir = createTempDirectory().toFile()
        val outputName = LambdaConstants.SCRIPT_CONTENT_FILENAME

        writeToFile(tempDir, outputName)

        val filename = unixCommandLinePreparer.writeBuildScriptContent(PROJECT_NAME, tempDir)

        Assert.assertEquals(filename, filename)

        val scriptFile = File("${tempDir.absolutePath}/$filename")
        Assert.assertTrue(scriptFile.exists())

        val content = String(Files.readAllBytes(scriptFile.toPath()))
        Assert.assertEquals(
            content,
            LambdaConstants.SCRIPT_CONTENT_HEADER + LambdaConstants.SCRIPT_CONTENT_CHANGE_DIRECTORY_PREFIX + SCRIPT_CONTENT
        )
    }

    private fun writeToFile(tempDir: File, outputName: String) {
        val scriptFile = File("${tempDir.absolutePath}/$outputName")
        scriptFile.createNewFile()

        val writer = scriptFile.printWriter()
        writer.println("some content")
        writer.close()
    }

    private fun createClient() = UnixCommandLinePreparer(context, logger)

    companion object {
        private const val PROJECT_NAME = "projectName"
        private const val SCRIPT_CONTENT = "scriptContent"
    }
}