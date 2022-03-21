package jetbrains.buildServer.runner.lambda.directory

import java.io.File

interface ArchiveManager {
    fun archiveDirectory(directory: File): File

    fun extractDirectory(tar: File?, destinationDirectory: File?)
}