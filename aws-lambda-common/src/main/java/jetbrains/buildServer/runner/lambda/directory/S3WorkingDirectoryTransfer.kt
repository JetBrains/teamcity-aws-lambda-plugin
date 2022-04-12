package jetbrains.buildServer.runner.lambda.directory

import com.amazonaws.services.s3.model.ObjectMetadata

interface S3WorkingDirectoryTransfer: WorkingDirectoryTransfer {

    val bucketName: String
    fun getValueProps(key: String): ObjectMetadata?
}