package jetbrains.buildServer.runner.lambda.directory

import com.amazonaws.AmazonServiceException
import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.HeadBucketRequest
import com.amazonaws.services.s3.model.PresignedUrlDownloadRequest
import com.amazonaws.services.s3.transfer.TransferManager
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.LambdaConstants.BUCKET_NAME
import java.io.File
import java.net.URL
import java.time.Instant
import java.util.*


class S3WorkingDirectoryTransfer(
    private val logger: Logger,
    private val transferManager: TransferManager,
    private val archiveManager: ArchiveManager
) :
    WorkingDirectoryTransfer {
    private val bucketName = "$BUCKET_NAME-${transferManager.amazonS3Client.regionName}"
    private val s3Client = transferManager.amazonS3Client

    private fun checkIfBucketExists(): Boolean =
        try {
            logger.message("Checking if S3 bucket $bucketName already exists...")
            val headBucketRequest = HeadBucketRequest(bucketName)
            s3Client.headBucket(headBucketRequest)
            true
        } catch (e: AmazonServiceException) {
            if (e.statusCode == 404 || e.statusCode == 403 || e.statusCode == 301) {
                false
            } else {
                throw e
            }
        }


    private fun createBucket() {
        logger.message("Bucket $bucketName does not exist, creating new now")
        s3Client.createBucket(bucketName)
    }

    override fun upload(key: String, workingDirectory: File): String {
        logger.message("Uploading working directory $workingDirectory to S3 bucket")
        if (!checkIfBucketExists()) {
            createBucket()
        }

        val workingDirectoryTar = archiveManager.archiveDirectory(workingDirectory)

        logger.message("Starting upload of working directory...")
        val upload = transferManager.upload(bucketName, key, workingDirectoryTar)
        upload.waitForCompletion()

        val generatePresignedUrlRequest = GeneratePresignedUrlRequest(bucketName, key).apply {
            method = HttpMethod.GET
            expiration = generateTimeout()
        }

        val url = s3Client.generatePresignedUrl(generatePresignedUrlRequest)

        logger.message("Upload complete")
        return url.toString()
    }

    private fun generateTimeout() = Date().apply {
        val expirationTimeMillis = Instant.now().toEpochMilli() + (1000 * 60 * LambdaConstants.S3_URL_TIMEOUT_MINUTES)
        time = expirationTimeMillis
    }

    override fun retrieve(url: String, destinationDirectory: File): File {
        logger.message("Downloading working directory from S3 bucket")
        val tempFile = kotlin.io.path.createTempFile().toFile()

        val presignedUrlDownload = PresignedUrlDownloadRequest(URL(url))
        val download = transferManager.download(presignedUrlDownload, tempFile)

        download.waitForCompletion()
        logger.message("Download complete")
        archiveManager.extractDirectory(tempFile, destinationDirectory)
        tempFile.deleteOnExit()
        return destinationDirectory
    }
}
