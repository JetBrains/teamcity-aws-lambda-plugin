package jetbrains.buildServer.runner.lambda.directory

import com.amazonaws.AmazonServiceException
import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.HeadBucketRequest
import com.amazonaws.services.s3.model.PresignedUrlDownloadRequest
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.transfer.PresignedUrlDownload
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.Upload
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.LambdaConstants
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import java.io.File
import java.net.URL
import java.time.Instant

@Listeners(MockitoTestNGListener::class)
class S3WorkingDirectoryTransferImplTest : BaseTestCase() {
    @Mock
    private lateinit var transferManager: TransferManager

    @Mock
    private lateinit var amazonS3: AmazonS3

    @Mock
    private lateinit var file: File

    @Mock
    private lateinit var logger: Logger

    @BeforeMethod
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        whenever(transferManager.amazonS3Client).thenReturn(amazonS3)
        whenever(amazonS3.regionName).thenReturn(REGION_NAME)
    }

    private fun verifyDirectoryIsUploaded(upload: Upload): KArgumentCaptor<PutObjectRequest> {
        val putObjectRequestCaptor = argumentCaptor<PutObjectRequest>()
        whenever(transferManager.upload(putObjectRequestCaptor.capture())).thenReturn(upload)
        return putObjectRequestCaptor
    }

    private fun verifyUrlIsGenerated(): KArgumentCaptor<GeneratePresignedUrlRequest> {
        val generatePresignedUrlRequestCaptor = argumentCaptor<GeneratePresignedUrlRequest>()
        whenever(amazonS3.generatePresignedUrl(generatePresignedUrlRequestCaptor.capture())).thenReturn(URL(MOCK_URL))
        return generatePresignedUrlRequestCaptor
    }

    private fun verifyObjectUpload(putObjectRequestCaptor: KArgumentCaptor<PutObjectRequest>, workDirectory: File, props: Map<String, String> = emptyMap<String, String>()) {
        val putObjectRequest = putObjectRequestCaptor.firstValue
        Assert.assertEquals(getBucketName(), putObjectRequest.bucketName)
        Assert.assertEquals(UPLOAD_KEY, putObjectRequest.key)
        Assert.assertEquals(workDirectory, putObjectRequest.file)
        Assert.assertEquals(props, putObjectRequest.metadata.userMetadata)
    }

    private fun verifyUrlGeneration(generatePresignedUrlRequestCaptor: KArgumentCaptor<GeneratePresignedUrlRequest>) {
        val item = generatePresignedUrlRequestCaptor.firstValue
        val interval = INTERVAL_IN_SECONDS * 1000
        val timeoutTime = Instant.now().toEpochMilli() + (LambdaConstants.S3_URL_TIMEOUT_MINUTES * 60 * 1000)
        val date = item.expiration.toInstant().toEpochMilli()

        Assert.assertEquals(getBucketName(), item.bucketName)
        Assert.assertEquals(HttpMethod.GET, item.method)
        Assert.assertTrue(date in (timeoutTime - interval)..(timeoutTime + interval))
    }

    @Test
    fun testUpload() {
        val s3WorkingDirectoryTransfer = createClient()
        val upload = mock<Upload>()
        val workDirectory = mockWorkDirectory()

        val putObjectRequestCaptor = verifyDirectoryIsUploaded(upload)
        val generatePresignedUrlRequestCaptor = verifyUrlIsGenerated()
        val url = s3WorkingDirectoryTransfer.upload(UPLOAD_KEY, workDirectory)
        verifyObjectUpload(putObjectRequestCaptor, workDirectory)
        verifyUrlGeneration(generatePresignedUrlRequestCaptor)
        Mockito.verify(amazonS3).headBucket(HeadBucketRequest(getBucketName()))
        Assert.assertEquals(url, MOCK_URL)
    }


    @Test
    fun testUpload_NonExistingBucket() {
        val s3WorkingDirectoryTransfer = createClient()
        val upload = mock<Upload>()
        val workDirectory = mockWorkDirectory()
        val amazonServiceException = mock<AmazonServiceException>()
        whenever(amazonS3.headBucket(HeadBucketRequest(getBucketName()))).thenThrow(amazonServiceException)
        whenever(amazonServiceException.statusCode).thenReturn(404)

        val putObjectRequestCaptor = verifyDirectoryIsUploaded(upload)
        val generatePresignedUrlRequestCaptor = verifyUrlIsGenerated()
        val url = s3WorkingDirectoryTransfer.upload(UPLOAD_KEY, workDirectory)
        Mockito.verify(amazonS3).createBucket(getBucketName())
        Assert.assertEquals(url, MOCK_URL)
        verifyObjectUpload(putObjectRequestCaptor, workDirectory)
        verifyUrlGeneration(generatePresignedUrlRequestCaptor)
    }

    @Test(expectedExceptions = [AmazonServiceException::class])
    fun testUpload_UnknownError() {
        val s3WorkingDirectoryTransfer = createClient()
        val workDirectory = mockWorkDirectory()
        val amazonServiceException = mock<AmazonServiceException>()
        whenever(amazonS3.headBucket(HeadBucketRequest(getBucketName()))).thenThrow(amazonServiceException)
        whenever(amazonServiceException.statusCode).thenReturn(500)

        val url = s3WorkingDirectoryTransfer.upload(UPLOAD_KEY, workDirectory)
        Mockito.verify(amazonServiceException).fillInStackTrace()
        Assert.assertEquals(url, MOCK_URL)
    }

    @Test
    fun testUpload_WithMetadata() {
        val s3WorkingDirectoryTransfer = createClient()
        val upload = mock<Upload>()
        val workDirectory = mockWorkDirectory()
        val props = mapOf(Pair("test", "test"))

        val putObjectRequestCaptor = verifyDirectoryIsUploaded(upload)
        val generatePresignedUrlRequestCaptor = verifyUrlIsGenerated()
        val url = s3WorkingDirectoryTransfer.upload(UPLOAD_KEY, workDirectory, props)
        Mockito.verify(amazonS3).headBucket(HeadBucketRequest(getBucketName()))
        Assert.assertEquals(url, MOCK_URL)
        verifyObjectUpload(putObjectRequestCaptor, workDirectory, props)
        verifyUrlGeneration(generatePresignedUrlRequestCaptor)
    }

    private fun mockWorkDirectory(): File {
        val file = mock<File>()
        whenever(file.toString()).thenReturn("mockWorkDirectory")
        return file
    }

    @Test
    fun testRetrieve() {
        val s3WorkingDirectoryTransfer = createClient()
        val download = mock<PresignedUrlDownload>()
        val presignedUrlRequestCaptor = argumentCaptor<PresignedUrlDownloadRequest>()
        whenever(transferManager.download(presignedUrlRequestCaptor.capture(), any())).thenReturn(download)

        s3WorkingDirectoryTransfer.retrieve(MOCK_URL)
        Mockito.verify(download).waitForCompletion()

    }

    private fun getBucketName() = "${LambdaConstants.BUCKET_NAME}-$REGION_NAME"

    private fun createClient() = S3WorkingDirectoryTransferImpl(logger, transferManager)


    companion object {
        private const val REGION_NAME = "regionName"
        private const val MOCK_URL = "http://www.mockUrl.com"
        private const val UPLOAD_KEY = "uploadKey"
        private const val INTERVAL_IN_SECONDS = 5
    }
}