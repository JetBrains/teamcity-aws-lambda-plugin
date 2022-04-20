package jetbrains.buildServer.runner.lambda.build

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.intellij.execution.configurations.GeneralCommandLine
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.DetachedBuildApi
import jetbrains.buildServer.runner.lambda.model.RunDetails
import kotlinx.coroutines.runBlocking
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File
import java.io.InputStream
import kotlin.random.Random

class LambdaCommandLineTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var generalCommandLine: GeneralCommandLine
    private lateinit var process: Process
    private lateinit var inputStream: InputStream
    private lateinit var errorStream: InputStream
    private lateinit var logger: LambdaLogger
    private lateinit var detachedBuildApi: DetachedBuildApi
    private lateinit var workingDirectory: File
    private lateinit var runDetails: RunDetails

    @BeforeMethod
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        generalCommandLine = m.mock(GeneralCommandLine::class.java)
        process = m.mock(Process::class.java)
        inputStream = m.mock(InputStream::class.java, "inputStream")
        errorStream = m.mock(InputStream::class.java, "errorStream")
        logger = m.mock(LambdaLogger::class.java)
        detachedBuildApi = m.mock(DetachedBuildApi::class.java)
        workingDirectory = m.mock(File::class.java)
        runDetails = RunDetails(USERNAME, PASSWORD, BUILD_ID, TEAMCITY_URl, SCRIPT_CONTENT, DIRECTORY_ID)
    }

    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }


    @Test
    fun testExecuteCommandLine() {
        val commandLine = LambdaCommandLine(generalCommandLine, logger)
        val numInputReads = 3
        val numErrorReads = 3

        m.checking(object : Expectations() {
            init {
                allowing(generalCommandLine).createProcess()
                will(returnValue(process))
                allowing(process).inputStream
                will(returnValue(inputStream))
                allowing(process).errorStream
                will(returnValue(errorStream))
                allowing(logger).log(with(any(String::class.java)))
                allowing(logger).log(with(any(ByteArray::class.java)))

                for (i in 0..numInputReads) {
                    oneOf(inputStream).read(with(any(ByteArray::class.java)))
                    will(returnValue(Random.nextInt(1, 5000)))
                    oneOf(detachedBuildApi).log(with(any(String::class.java)))
                }


                for (i in 0..numErrorReads) {
                    oneOf(errorStream).read(with(any(ByteArray::class.java)))
                    will(returnValue(Random.nextInt(1, 5000)))
                    oneOf(detachedBuildApi).logWarning(with(any(String::class.java)))
                }

                oneOf(inputStream).read(with(any(ByteArray::class.java)))
                will(returnValue(-1))
                oneOf(errorStream).read(with(any(ByteArray::class.java)))
                will(returnValue(-1))
                oneOf(process).waitFor()
                will(returnValue(0))
                oneOf(detachedBuildApi).log(with("Process finished with exit code 0\n"))
            }
        })

        runBlocking {
            commandLine.executeCommandLine(detachedBuildApi)
        }
    }

    @Test
    fun testExecuteCommandLine_withErrorProcess() {
        val commandLine = LambdaCommandLine(generalCommandLine, logger)
        val numInputReads = 3
        val numErrorReads = 3

        m.checking(object : Expectations() {
            init {
                allowing(generalCommandLine).createProcess()
                will(returnValue(process))
                allowing(process).inputStream
                will(returnValue(inputStream))
                allowing(process).errorStream
                will(returnValue(errorStream))
                allowing(logger).log(with(any(String::class.java)))
                allowing(logger).log(with(any(ByteArray::class.java)))

                for (i in 0..numInputReads) {
                    oneOf(inputStream).read(with(any(ByteArray::class.java)))
                    will(returnValue(Random.nextInt(1, 5000)))
                    oneOf(detachedBuildApi).log(with(any(String::class.java)))
                }


                for (i in 0..numErrorReads) {
                    oneOf(errorStream).read(with(any(ByteArray::class.java)))
                    will(returnValue(Random.nextInt(1, 5000)))
                    oneOf(detachedBuildApi).logWarning(with(any(String::class.java)))
                }

                oneOf(inputStream).read(with(any(ByteArray::class.java)))
                will(returnValue(-1))
                oneOf(errorStream).read(with(any(ByteArray::class.java)))
                will(returnValue(-1))
                oneOf(process).waitFor()
                will(returnValue(1))
                oneOf(detachedBuildApi).failBuild(ProcessFailedException(("Process finished with exit code 1\n")))
            }
        })


        runBlocking {
            commandLine.executeCommandLine(detachedBuildApi)
        }
    }

    @Test
    fun testCreateCommandLine() {
        m.checking(object : Expectations() {
            init {
                allowing(workingDirectory).absolutePath
                will(returnValue(ABSOLUTE_PATH))
            }
        })

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
        private const val USERNAME = "username"
        private const val PASSWORD = "password"
        private const val SCRIPT_CONTENT = "scriptContent"
        private const val DIRECTORY_ID = "directoryId"
        private const val ABSOLUTE_PATH = "absolutePath"
    }

}