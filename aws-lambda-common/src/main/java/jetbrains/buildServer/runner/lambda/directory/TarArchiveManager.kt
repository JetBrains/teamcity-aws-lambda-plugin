package jetbrains.buildServer.runner.lambda.directory

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.utils.IOUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission


class TarArchiveManager : ArchiveManager {
    override fun archiveDirectory(directory: File): File {
        val tarBall = kotlin.io.path.createTempFile().toFile()

        val output = TarArchiveOutputStream(GzipCompressorOutputStream(tarBall.outputStream()))

        val files = recurseDirectory(directory)

        files.forEach {
            val entry = TarArchiveEntry(it, directory.toPath().relativize(it.toPath()).toString()).apply {
                size = it.length()
                mode = modeFromFilePermissions(Files.getPosixFilePermissions(it.toPath()))
            }

            output.putArchiveEntry(entry)
            IOUtils.copy(it.inputStream(), output)
            output.closeArchiveEntry()
        }

        output.finish()
        output.close()
        return tarBall
    }

    private fun recurseDirectory(directory: File): List<File> {
        val files = mutableListOf<File>()
        if (directory.isDirectory) {
            for (file in directory.listFiles()!!) {
                if (file.isDirectory) {
                    files.addAll(recurseDirectory(file))
                } else {
                    files.add(file)
                }
            }
        }
        return files
    }

    override fun extractDirectory(tar: File?, destinationDirectory: File?) {
        if (tar == null || destinationDirectory == null) {
            return
        }

        val input = TarArchiveInputStream(GzipCompressorInputStream(tar.inputStream()))

        var entry: TarArchiveEntry?

        while ((input.nextTarEntry.also { entry = it }) != null) {
            if (!input.canReadEntryData(entry)) {
                //TODO TW-75451: Add logs
                continue
            }

            val name = "${destinationDirectory.absolutePath}/${entry!!.name}"
            val file = File(name)
            if (entry!!.isDirectory) {
                makeDirectory(file)
            } else {
                makeDirectory(file.parentFile)
                val output = file.outputStream()
                IOUtils.copy(input, output)
                output.flush()
                output.close()
                Files.setPosixFilePermissions(
                    file.toPath(),
                    permissionsFromInt(entry!!.mode)
                )
            }

        }
        input.close()
    }

    private fun makeDirectory(file: File) {
        if (!file.isDirectory && !file.mkdirs()) {
            throw IOException("Failed to create directory $file")
        }
    }

    companion object {
        private fun String.toOctal() = this.toInt(radix = 8)

        private val intToPosixFilePermission = mapOf(
            Pair("0400".toOctal(), PosixFilePermission.OWNER_READ),
            Pair("0200".toOctal(), PosixFilePermission.OWNER_WRITE),
            Pair("0100".toOctal(), PosixFilePermission.OWNER_EXECUTE),
            Pair("0040".toOctal(), PosixFilePermission.GROUP_READ),
            Pair("0020".toOctal(), PosixFilePermission.GROUP_WRITE),
            Pair("0010".toOctal(), PosixFilePermission.GROUP_EXECUTE),
            Pair("0004".toOctal(), PosixFilePermission.OTHERS_READ),
            Pair("0002".toOctal(), PosixFilePermission.OTHERS_WRITE),
            Pair("0001".toOctal(), PosixFilePermission.OTHERS_EXECUTE),
        )

        private val posixToIntFilePermission = mapOf(
            Pair(PosixFilePermission.OWNER_READ, "0400".toOctal()),
            Pair(PosixFilePermission.OWNER_WRITE, "0200".toOctal()),
            Pair(PosixFilePermission.OWNER_EXECUTE, "0100".toOctal()),
            Pair(PosixFilePermission.GROUP_READ, "0040".toOctal()),
            Pair(PosixFilePermission.GROUP_WRITE, "0020".toOctal()),
            Pair(PosixFilePermission.GROUP_EXECUTE, "0010".toOctal()),
            Pair(PosixFilePermission.OTHERS_READ, "0004".toOctal()),
            Pair(PosixFilePermission.OTHERS_WRITE, "0002".toOctal()),
            Pair(PosixFilePermission.OTHERS_EXECUTE, "0001".toOctal()),
        )

        private fun permissionsFromInt(mode: Int): Set<PosixFilePermission> {
            val permissionSet = mutableSetOf<PosixFilePermission>()
            intToPosixFilePermission.forEach { (key, posixFilePermission) ->
                if (mode and key > 0) {
                    permissionSet.add(posixFilePermission)
                }
            }

            return permissionSet
        }

        private fun modeFromFilePermissions(filePermissions: Set<PosixFilePermission>): Int {
            var mode = 0
            posixToIntFilePermission.forEach { (permission, value) ->
                if (filePermissions.contains(permission)) {
                    mode = mode or value
                }

            }

            return mode
        }
    }
}