package jetbrains.buildServer.runner.lambda.directory

import com.intellij.execution.configurations.GeneralCommandLine
import jetbrains.buildServer.BaseTestCase
import org.jmock.Mockery
import org.mockito.Mock
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

@Listeners(MockitoTestNGListener::class)
class TarArchiveManagerTest : BaseTestCase() {
    @Mock
    private lateinit var logger: Logger

    @Test
    fun testArchiveAndExtract() {
        val archiveManager = TarArchiveManager(logger)
        val directory = createDirectoryWithFile()

        val archive = archiveManager.archiveDirectory(directory)

        val destinationDir = createTempDir()
        archiveManager.extractDirectory(archive, destinationDir)

        val childDir = File(destinationDir, DIRECTORY)
        Assert.assertTrue(childDir.exists())
        Assert.assertTrue(childDir.isDirectory)

        val file = File(childDir, FILE)
        Assert.assertTrue(file.exists())
        Assert.assertTrue(file.isFile)

        val content = file.readText()
        Assert.assertEquals(content, FILE_CONTENT)

        val generalCommandLine = GeneralCommandLine().apply {
            exePath = "./$DIRECTORY/$FILE"
            setWorkingDirectory(destinationDir)
        }

        val process = generalCommandLine.createProcess()

        val message = String(process.inputStream.readAllBytes())
        Assert.assertEquals(message, "$MESSAGE\n")
    }

    private fun createDirectoryWithFile(): File {
        val directory = createTempDir()
        val childDir = File(directory, DIRECTORY)
        childDir.mkdirs()

        val file = File(childDir, FILE)
        file.createNewFile()
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
        val writer = file.printWriter()
        writer.print(FILE_CONTENT)
        writer.flush()
        writer.close()

        return directory
    }

    companion object {
        const val DIRECTORY = "directory"
        const val FILE = "file"
        const val MESSAGE = "test"
        const val FILE_CONTENT = "echo $MESSAGE"
    }
}