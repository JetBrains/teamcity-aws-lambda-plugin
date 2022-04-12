package jetbrains.buildServer.runner.lambda.directory

import com.amazonaws.AmazonServiceException
import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.model.*
import com.amazonaws.services.s3.transfer.TransferManager
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.LambdaConstants.BUCKET_NAME
import java.io.File
import java.net.URL
import java.time.Instant
import java.util.*


class S3WorkingDirectoryTransferImpl(
    private val logger: Logger,
    private val transferManager: TransferManager
) :
    S3WorkingDirectoryTransfer {
    private val name = "$BUCKET_NAME-${transferManager.amazonS3Client.regionName}"
    private val s3Client = transferManager.amazonS3Client

    private fun checkIfBucketExists(): Boolean =
        try {
            logger.message("Checking if S3 bucket ${this.name} already exists...")
            val headBucketRequest = HeadBucketRequest(this.name)
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
        logger.message("Bucket ${this.name} does not exist, creating new now")
        s3Client.createBucket(this.name)
    }

    override val bucketName: String
        get() = this.name

    override fun getValueProps(key: String): ObjectMetadata? = try {
        val getObjectMetadataRequest = GetObjectMetadataRequest(this.name, key)
        s3Client.getObjectMetadata(getObjectMetadataRequest)
    } catch (e: AmazonServiceException) {
        if (e.statusCode == 404) {
            null
        } else {
            throw e
        }
    }

    override fun upload(key: String, workingDirectory: File?, properties: Map<String, String>?): String {
        logger.message("Uploading working directory $workingDirectory to S3 bucket")
        if (!checkIfBucketExists()) {
            createBucket()
        }

        logger.message("Starting upload of working directory...")
        val objectMetadata = ObjectMetadata().apply {
            contentType = "plain/text"
            properties?.forEach { (key, value) -> addUserMetadata(key, value) }
        }
        val putObjectRequest = PutObjectRequest(this.name, key, workingDirectory).apply {
            metadata = objectMetadata
        }
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()

        val generatePresignedUrlRequest = GeneratePresignedUrlRequest(this.name, key).apply {
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

    override fun retrieve(url: String): File {
        logger.message("Downloading working directory from S3 bucket")
        val tempFile = kotlin.io.path.createTempFile(prefix = LambdaConstants.FILE_PREFIX).toFile()

        val presignedUrlDownload = PresignedUrlDownloadRequest(URL(url))
        val download = transferManager.download(presignedUrlDownload, tempFile)

        download.waitForCompletion()
        logger.message("Download complete")
        return tempFile
    }
}
