package jetbrains.buildServer.runner.lambda.directory

import java.io.File

interface WorkingDirectoryTransfer {
    fun upload(key: String, workingDirectory: File?, properties: Map<String, String>? = emptyMap()): String
    fun retrieve(directoryId: String): File
}