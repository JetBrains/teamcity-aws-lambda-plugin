package jetbrains.buildServer.runner.lambda.directory

import MockLoggerObject.mockBuildLogger
import com.amazonaws.AmazonServiceException
import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.HeadBucketRequest
import com.amazonaws.services.s3.model.PresignedUrlDownloadRequest
import com.amazonaws.services.s3.transfer.PresignedUrlDownload
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.Upload
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.LambdaConstants
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File
import java.net.URL
import java.time.Instant

class S3WorkingDirectoryTransferTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var transferManager: TransferManager
    private lateinit var amazonS3: AmazonS3
    private lateinit var archiveManager: ArchiveManager
    private lateinit var file: File
    private lateinit var logger: Logger

    @BeforeMethod
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        transferManager = m.mock(TransferManager::class.java)
        amazonS3 = m.mock(AmazonS3::class.java)
        archiveManager = m.mock(ArchiveManager::class.java)
        file = m.mock(File::class.java, "TarFile")
        logger = m.mockBuildLogger()

        m.checking(object : Expectations() {
            init {
                allowing(transferManager).amazonS3Client
                will(returnValue(amazonS3))
                allowing(amazonS3).regionName
                will(returnValue(REGION_NAME))
            }
        })
    }

    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }

    private val generatePresignedUrlRequest = object : TypeSafeMatcher<GeneratePresignedUrlRequest>() {
        private val intervalInSeconds = 5
        override fun describeTo(description: Description) {
            description.appendText("Compare GeneratePresignedUrlRequest's")
        }

        override fun matchesSafely(item: GeneratePresignedUrlRequest): Boolean {
            val interval = intervalInSeconds * 1000
            val timeoutTime = Instant.now().toEpochMilli() + (LambdaConstants.S3_URL_TIMEOUT_MINUTES * 60 * 1000)
            val date = item.expiration.toInstant().toEpochMilli()

            return item.bucketName == getBucketName() &&
                    item.method == HttpMethod.GET &&
                    date in (timeoutTime - interval)..(timeoutTime + interval)

        }

    }

    private val presignedUrlDownloadRequestMatcher = object : TypeSafeMatcher<PresignedUrlDownloadRequest>() {
        override fun describeTo(description: Description) {
            description.appendText("Compare UrlDownloadRequest. This is required since the PresignedUrlDownloadRequest does not offer an equals")
        }

        override fun matchesSafely(item: PresignedUrlDownloadRequest): Boolean {
            return item.presignedUrl == URL(MOCK_URL)
        }

    }

    private fun verifyDirectoryIsUploaded(workDirectory: File, upload: Upload) {

        m.checking(object : Expectations() {
            init {
                oneOf(archiveManager).archiveDirectory(workDirectory)
                will(returnValue(file))
                oneOf(transferManager).upload(
                    getBucketName(),
                    UPLOAD_KEY,
                    file
                )
                will(returnValue(upload))
                oneOf(upload).waitForCompletion()

                oneOf(amazonS3).generatePresignedUrl(
                    with(generatePresignedUrlRequest)
                )
                will(returnValue(URL(MOCK_URL)))
            }
        })

    }

    @Test
    fun testUpload() {
        val s3WorkingDirectoryTransfer = createClient()
        val upload = m.mock(Upload::class.java)
        val workDirectory = m.mock(File::class.java)

        m.checking(object : Expectations() {
            init {
                oneOf(amazonS3).headBucket(HeadBucketRequest(getBucketName()))
                verifyDirectoryIsUploaded(workDirectory, upload)
            }
        })

        val url = s3WorkingDirectoryTransfer.upload(UPLOAD_KEY, workDirectory)
        Assert.assertEquals(url, MOCK_URL)
    }


    @Test
    fun testUpload_NonExistingBucket() {
        val s3WorkingDirectoryTransfer = createClient()
        val upload = m.mock(Upload::class.java)
        val workDirectory = m.mock(File::class.java)
        val amazonServiceException = m.mock(AmazonServiceException::class.java)

        m.checking(object : Expectations() {
            init {
                oneOf(amazonS3).headBucket(HeadBucketRequest(getBucketName()))
                will(throwException(amazonServiceException))
                oneOf(amazonServiceException).statusCode
                will(returnValue(404))
                oneOf(amazonServiceException).fillInStackTrace()

                oneOf(amazonS3).createBucket(getBucketName())

                verifyDirectoryIsUploaded(workDirectory, upload)
            }
        })

        val url = s3WorkingDirectoryTransfer.upload(UPLOAD_KEY, workDirectory)
        Assert.assertEquals(url, MOCK_URL)
    }


    @Test(expectedExceptions = [AmazonServiceException::class])
    fun testUpload_UnknownError() {
        val s3WorkingDirectoryTransfer = createClient()
        val workDirectory = m.mock(File::class.java)
        val amazonServiceException = m.mock(AmazonServiceException::class.java)

        m.checking(object : Expectations() {
            init {
                oneOf(amazonS3).headBucket(HeadBucketRequest(getBucketName()))
                will(throwException(amazonServiceException))
                allowing(amazonServiceException).statusCode
                will(returnValue(500))
                oneOf(amazonServiceException).fillInStackTrace()
            }
        })

        val url = s3WorkingDirectoryTransfer.upload(UPLOAD_KEY, workDirectory)
        Assert.assertEquals(url, MOCK_URL)
    }

    @Test
    fun testRetrieve() {
        val s3WorkingDirectoryTransfer = createClient()
        val download = m.mock(PresignedUrlDownload::class.java)
        val destinationDirectory = m.mock(File::class.java)

        m.checking(object : Expectations() {
            init {
                oneOf(transferManager).download(
                    with(presignedUrlDownloadRequestMatcher),
                    with(any(File::class.java))
                )
                will(returnValue(download))
                oneOf(download).waitForCompletion()
                oneOf(archiveManager).extractDirectory(with(any(File::class.java)), with(destinationDirectory))
            }
        })

        s3WorkingDirectoryTransfer.retrieve(MOCK_URL, destinationDirectory)
    }

    private fun getBucketName() = "${LambdaConstants.BUCKET_NAME}-$REGION_NAME"

    private fun createClient() = S3WorkingDirectoryTransfer(logger, transferManager, archiveManager)


    companion object {
        private const val REGION_NAME = "regionName"
        private const val MOCK_URL = "http://www.mockUrl.com"
        private const val UPLOAD_KEY = "uploadKey"
    }
}