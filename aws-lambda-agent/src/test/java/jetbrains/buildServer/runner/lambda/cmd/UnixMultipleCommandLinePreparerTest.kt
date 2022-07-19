package jetbrains.buildServer.runner.lambda.cmd

import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import org.jmock.Mockery
import org.mockito.Mock
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

private const val s = "scriptContent-1"

@Listeners(MockitoTestNGListener::class)
class UnixMultipleCommandLinePreparerTest : BaseTestCase() {
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

    private fun getFilenames() = listOf("${LambdaConstants.SCRIPT_CONTENT_FILENAME}-0", "${LambdaConstants.SCRIPT_CONTENT_FILENAME}-1")

    @Test
    fun testWriteBuildScriptContent() {
        val unixCommandLinePreparer = createClient()
        val tempDir = createTempDirectory().toFile()

        writeBuildScriptContent(unixCommandLinePreparer, tempDir)
    }

    @Test
    fun testWriteBuildScriptContent_ExistingFile() {
        val unixCommandLinePreparer = createClient()
        val tempDir = createTempDirectory().toFile()
        val outputNames = getFilenames()

        outputNames.forEach {
            writeToFile(tempDir, it)
        }

        writeBuildScriptContent(unixCommandLinePreparer, tempDir)
    }

    private fun writeBuildScriptContent(unixCommandLinePreparer: UnixMultipleCommandLinePreparer, tempDir: File) {
        val filenameList = unixCommandLinePreparer.writeBuildScriptContent(PROJECT_NAME, tempDir)

        Assert.assertEquals(filenameList, getFilenames())
        filenameList.forEachIndexed { index, filename ->
            val scriptFile = File("${tempDir.absolutePath}/${filename}")
            Assert.assertTrue(scriptFile.exists())

            val content = String(Files.readAllBytes(scriptFile.toPath())).trim()
            val expectedContent = (LambdaConstants.SCRIPT_CONTENT_HEADER + LambdaConstants.SCRIPT_CONTENT_CHANGE_DIRECTORY_PREFIX + "$SCRIPT_CONTENT_VALUE-$index").trim()
            Assert.assertEquals(
                content,
                expectedContent
            )
        }
    }

    private fun writeToFile(tempDir: File, outputName: String) {
        val scriptFile = File("${tempDir.absolutePath}/$outputName")
        scriptFile.createNewFile()

        val writer = scriptFile.printWriter()
        writer.println("some content")
        writer.close()
    }

    private fun createClient() = UnixMultipleCommandLinePreparer(context, logger)


    companion object {
        private const val PROJECT_NAME = "projectName"
        private const val SCRIPT_CONTENT_VALUE = "scriptContent"
        private const val SCRIPT_CONTENT = "$SCRIPT_CONTENT_VALUE-0 ${LambdaConstants.SCRIPT_CONTENT_SPLITTER}$SCRIPT_CONTENT_VALUE-1"
    }
}