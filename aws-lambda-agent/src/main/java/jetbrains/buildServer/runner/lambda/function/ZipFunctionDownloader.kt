package jetbrains.buildServer.runner.lambda.function

import jetbrains.buildServer.util.TCStreamUtil
import java.net.URL
import java.nio.ByteBuffer

class ZipFunctionDownloader(private val url: String): FunctionDownloader {
    override fun downloadFunctionCode(): ByteBuffer{
        val fileStream = URL(url).openStream()
        val tempFile = kotlin.io.path.createTempFile().toFile()

        TCStreamUtil.writeBinary(fileStream, tempFile.outputStream())

        return ByteBuffer.wrap(tempFile.readBytes())
    }
}