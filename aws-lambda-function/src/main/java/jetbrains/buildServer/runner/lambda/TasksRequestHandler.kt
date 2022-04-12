package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.engine.cio.*
import jetbrains.buildServer.runner.lambda.LambdaConstants.FILE_PREFIX
import jetbrains.buildServer.runner.lambda.build.LambdaCommandLine
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransferImpl
import jetbrains.buildServer.runner.lambda.directory.TarArchiveManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.path.createTempDirectory

@ExperimentalCoroutinesApi
class TasksRequestHandler : RequestStreamHandler {
    private val objectMapper = jacksonObjectMapper()

    override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
        val logger = object : Logger {
            override fun message(message: String?) {
                context.logger.log(message)
            }
        }

        val runDetails: RunDetails = objectMapper.readValue(input)
        val detachedBuildApi = MyDetachedBuildApi(
            runDetails, context, CIO.create(), newSingleThreadContext(MyDetachedBuildApi::class.toString())
        )

        try {
            val archiveManager = TarArchiveManager(
                logger
            )
            val workingDirectoryTransfer = S3WorkingDirectoryTransferImpl(
                logger, getTransferManager()
            )

            cleanTempDirectory()

            val workingDirectoryArchive =
                workingDirectoryTransfer.retrieve(runDetails.directoryId)
            val destinationDirectory = createTempDirectory(prefix = FILE_PREFIX).toFile()
            archiveManager.extractDirectory(workingDirectoryArchive, destinationDirectory)

            runBlocking {
                detachedBuildApi.startLogging()
                LambdaCommandLine(runDetails, context.logger, destinationDirectory).executeCommandLine(detachedBuildApi)
            }
        } catch (e: Throwable) {
            context.logger.log("Exception during the execution: $e")
            runBlocking {
                detachedBuildApi.failBuild(e).join()
            }
        } finally {
            runBlocking {
                detachedBuildApi.finishBuild()
                detachedBuildApi.stopLogging()
            }
        }
    }

    private fun cleanTempDirectory() {
        val tmpDir = File("/tmp")

        tmpDir.listFiles { _, name ->
            name.startsWith(FILE_PREFIX)
        }?.forEach { workingDirectory ->
            workingDirectory.deleteRecursively()
        }
    }

    private fun getTransferManager(): TransferManager = TransferManagerBuilder.standard().build()
}
