package jetbrains.buildServer.runner.lambda.directory

import java.io.File

interface WorkingDirectoryTransfer {
    fun upload(key: String, workingDirectory: File): String
    fun retrieve(directoryId: String): File
}