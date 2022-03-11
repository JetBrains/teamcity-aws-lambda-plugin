package jetbrains.buildServer.runner.lambda.directory

import java.io.File

interface WorkingDirectoryTransfer {
    fun upload(workingDirectory: File): String
    fun retrieve(directoryId: String, destinationDirectory: File): File
}