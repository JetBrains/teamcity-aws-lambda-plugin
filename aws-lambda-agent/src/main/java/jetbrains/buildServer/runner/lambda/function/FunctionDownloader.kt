package jetbrains.buildServer.runner.lambda.function

import java.nio.ByteBuffer

interface FunctionDownloader {
    fun downloadFunctionCode(): ByteBuffer
}
