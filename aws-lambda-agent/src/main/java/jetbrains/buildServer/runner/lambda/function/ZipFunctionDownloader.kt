package jetbrains.buildServer.runner.lambda.function

import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels

class ZipFunctionDownloader(private val url: String): FunctionDownloader {
    override fun downloadFunctionCode(): ByteBuffer{
        val readableByteChannel = Channels.newChannel(URL(url).openStream())
        val tempFile = kotlin.io.path.createTempFile().toFile()
        val tempFileChannel = tempFile.outputStream().channel

        tempFileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE)

        return ByteBuffer.wrap(tempFile.readBytes())
    }
}