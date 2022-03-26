package jetbrains.buildServer.runner.lambda.function

import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.util.TCStreamUtil
import java.net.URL
import java.nio.ByteBuffer

class ZipFunctionDownloader(private val logger: BuildProgressLogger, private val url: String) : FunctionDownloader {
    override fun downloadFunctionCode(): ByteBuffer {
        logger.message("Downloading function's ZIP...")
        val fileStream = URL(url).openStream()
        val tempFile = kotlin.io.path.createTempFile().toFile()

        TCStreamUtil.writeBinary(fileStream, tempFile.outputStream())

        logger.message("Download finished successfully...")
        return ByteBuffer.wrap(tempFile.readBytes())
    }
}