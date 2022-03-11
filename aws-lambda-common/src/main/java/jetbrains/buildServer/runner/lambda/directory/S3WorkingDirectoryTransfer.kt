package jetbrains.buildServer.runner.lambda.directory

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.model.HeadBucketRequest
import com.amazonaws.services.s3.transfer.TransferManager
import jetbrains.buildServer.runner.lambda.LambdaConstants.BUCKET_NAME
import java.io.File
import java.util.*


class S3WorkingDirectoryTransfer(private val transferManager: TransferManager) :
    WorkingDirectoryTransfer {
    private val bucketName = "$BUCKET_NAME-${transferManager.amazonS3Client.regionName}"
    private val s3Client = transferManager.amazonS3Client

    private fun checkIfBucketExists(): Boolean =
        try {
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
        s3Client.createBucket(bucketName)
    }

    override fun upload(workingDirectory: File): String {
        if (!checkIfBucketExists()) {
            createBucket()
        }

        val key = UUID.randomUUID().toString()
        val multipleFileUpload = transferManager.uploadDirectory(bucketName, key, workingDirectory, true)
        multipleFileUpload.waitForCompletion()
        return key
    }

    override fun retrieve(key: String, destinationDirectory: File): File {
        val multiFileDownload = transferManager.downloadDirectory(bucketName, key, destinationDirectory)

        multiFileDownload.waitForCompletion()
        return destinationDirectory
    }
}