package jetbrains.buildServer.runner.lambda.build

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.intellij.execution.configurations.GeneralCommandLine
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.DetachedBuildApi
import jetbrains.buildServer.runner.lambda.model.BuildDetails
import jetbrains.buildServer.runner.lambda.model.RunDetails
import kotlinx.coroutines.runBlocking
import org.mockito.BDDMockito.will
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import java.io.File
import java.io.InputStream
import kotlin.random.Random

@Listeners(MockitoTestNGListener::class)
class LambdaCommandLineTest : BaseTestCase() {
    @Mock
    private lateinit var generalCommandLine: GeneralCommandLine
    @Mock
    private lateinit var process: Process
    @Mock
    private lateinit var inputStream: InputStream
    @Mock
    private lateinit var errorStream: InputStream
    @Mock
    private lateinit var logger: LambdaLogger
    @Mock
    private lateinit var detachedBuildApi: DetachedBuildApi
    @Mock
    private lateinit var workingDirectory: File
    @Mock
    private lateinit var runDetails: RunDetails

    @BeforeMethod
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        runDetails = RunDetails(USERNAME, PASSWORD, TEAMCITY_URl, SCRIPT_CONTENT, DIRECTORY_ID, RUN_NUMBER, BuildDetails(BUILD_ID, BUILD_TYPE_ID, AGENT_NAME))
    }

    @Test
    fun testExecuteCommandLine() {
        val commandLine = LambdaCommandLine(generalCommandLine, logger)
        val numInputReads = 3
        val numErrorReads = 3
        whenever(generalCommandLine.createProcess()).thenReturn(process)
        whenever(process.inputStream).thenReturn(inputStream)
        whenever(process.errorStream).thenReturn(errorStream)
        whenever(inputStream.read(any())).thenReturn(getRandomInt(), getRandomInt(), getRandomInt(), -1)
        whenever(errorStream.read(any())).thenReturn(getRandomInt(), getRandomInt(), getRandomInt(), -1)
        whenever(process.waitFor()).thenReturn(0)

        runBlocking {
            commandLine.executeCommandLine(detachedBuildApi)
        }
        Mockito.verify(detachedBuildApi, Mockito.times(numInputReads + 1)).log(Mockito.anyString())
        Mockito.verify(detachedBuildApi, Mockito.times(numErrorReads + 1)).log(Mockito.anyString())
        Mockito.verify(detachedBuildApi).log("Process finished with exit code 0\n")
    }

    @Test
    fun testExecuteCommandLine_withErrorProcess() {
        val commandLine = LambdaCommandLine(generalCommandLine, logger)
        val numInputReads = 3
        val numErrorReads = 3
        whenever(generalCommandLine.createProcess()).thenReturn(process)
        whenever(process.inputStream).thenReturn(inputStream)
        whenever(process.errorStream).thenReturn(errorStream)
        whenever(inputStream.read(any())).thenReturn(getRandomInt(), getRandomInt(), getRandomInt(), getRandomInt(), -1)
        whenever(errorStream.read(any())).thenReturn(getRandomInt(), getRandomInt(), getRandomInt(), getRandomInt(), -1)
        whenever(process.waitFor()).thenReturn(1)

        runBlocking {
            commandLine.executeCommandLine(detachedBuildApi)
        }
        Mockito.verify(detachedBuildApi, Mockito.times(numInputReads + 1)).log(Mockito.anyString())
        Mockito.verify(detachedBuildApi, Mockito.times(numErrorReads + 1)).log(Mockito.anyString())
        Mockito.verify(detachedBuildApi).failBuild(ProcessFailedException("Process finished with exit code 1\n"))
    }

    private fun getRandomInt() = Random.nextInt(1, 5000)

    @Test
    fun testCreateCommandLine() {
        whenever(workingDirectory.absolutePath).thenReturn(ABSOLUTE_PATH)

        val generalCommandLine = LambdaCommandLine.createCommandLine(workingDirectory, runDetails)
        Assert.assertEquals(generalCommandLine.exePath, "/bin/bash")
        Assert.assertEquals(generalCommandLine.workDirectory, workingDirectory)
        Assert.assertEquals(generalCommandLine.envParams, System.getenv())
        Assert.assertEquals(
            generalCommandLine.parametersList.list,
            listOf("$ABSOLUTE_PATH/${runDetails.customScriptFilename}")
        )
    }

    companion object {
        private const val TEAMCITY_URl = "http://teamcityUrl"
        private const val BUILD_ID = "buildId"
        private const val BUILD_TYPE_ID = "buildTypeId"
        private const val AGENT_NAME = "agentName"
        private const val USERNAME = "username"
        private const val PASSWORD = "password"
        private const val SCRIPT_CONTENT = "scriptContent"
        private const val DIRECTORY_ID = "directoryId"
        private const val ABSOLUTE_PATH = "absolutePath"
        private const val RUN_NUMBER = 0
    }

}