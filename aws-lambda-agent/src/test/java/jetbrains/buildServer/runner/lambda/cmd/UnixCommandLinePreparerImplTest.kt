package jetbrains.buildServer.runner.lambda.cmd

import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import org.mockito.Mock
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

@Listeners(MockitoTestNGListener::class)
class UnixCommandLinePreparerImplTest : BaseTestCase() {

    @Mock
    private lateinit var context: BuildRunnerContext

    @Mock
    private lateinit var logger: BuildProgressLogger

    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()
        whenever(context.runnerParameters).thenReturn(
            mapOf(
                Pair(LambdaConstants.SCRIPT_CONTENT_PARAM, SCRIPT_CONTENT)
            )
        )
    }

    @Test
    fun testWriteBuildScriptContent() {
        val unixCommandLinePreparer = createClient()
        val tempDir = createTempDirectory().toFile()

        val filenameList = unixCommandLinePreparer.writeBuildScriptContent(PROJECT_NAME, tempDir)

        Assert.assertEquals(filenameList, listOf(LambdaConstants.SCRIPT_CONTENT_FILENAME))

        val scriptFile = File("${tempDir.absolutePath}/${filenameList.first()}")
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

        val filenameList = unixCommandLinePreparer.writeBuildScriptContent(PROJECT_NAME, tempDir)

        Assert.assertEquals(filenameList, listOf(outputName))

        val scriptFile = File("${tempDir.absolutePath}/${filenameList.first()}")
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

    private fun createClient() = UnixCommandLinePreparerImpl(context, logger)

    companion object {
        private const val PROJECT_NAME = "projectName"
        private const val SCRIPT_CONTENT = "scriptContent"
    }
}