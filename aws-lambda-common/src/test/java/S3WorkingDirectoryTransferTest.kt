package jetbrains.buildServer.runner.lambda.directory

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.HeadBucketRequest
import com.amazonaws.services.s3.transfer.MultipleFileDownload
import com.amazonaws.services.s3.transfer.MultipleFileUpload
import com.amazonaws.services.s3.transfer.TransferManager
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.runner.lambda.LambdaConstants
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File

class S3WorkingDirectoryTransferTest : BaseTestCase() {
    private lateinit var m: Mockery
    private lateinit var transferManager: TransferManager
    private lateinit var amazonS3: AmazonS3

    @BeforeMethod
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        transferManager = m.mock(TransferManager::class.java)
        amazonS3 = m.mock(AmazonS3::class.java)

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

    @Test
    fun testUpload() {
        val s3WorkingDirectoryTransfer = createClient()
        val multipleFileUpload = m.mock(MultipleFileUpload::class.java)
        val workDirectory = m.mock(File::class.java)

        m.checking(object : Expectations() {
            init {
                oneOf(amazonS3).headBucket(HeadBucketRequest(getBucketName()))
                oneOf(transferManager).uploadDirectory(
                    with(getBucketName()),
                    with(any(String::class.java)),
                    with(workDirectory),
                    with(true)
                )
                will(returnValue(multipleFileUpload))
                oneOf(multipleFileUpload).waitForCompletion()
            }
        })

        s3WorkingDirectoryTransfer.upload(workDirectory)
    }


    @Test
    fun testUpload_NonExistingBucket() {
        val s3WorkingDirectoryTransfer = createClient()
        val multipleFileUpload = m.mock(MultipleFileUpload::class.java)
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

                oneOf(transferManager).uploadDirectory(
                    with(getBucketName()),
                    with(any(String::class.java)),
                    with(workDirectory),
                    with(true)
                )
                will(returnValue(multipleFileUpload))
                oneOf(multipleFileUpload).waitForCompletion()
            }
        })

        s3WorkingDirectoryTransfer.upload(workDirectory)
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

        s3WorkingDirectoryTransfer.upload(workDirectory)
    }

    @Test
    fun testRetrieve() {
        val s3WorkingDirectoryTransfer = createClient()
        val multipleFileDownload = m.mock(MultipleFileDownload::class.java)
        val destinationDirectory = m.mock(File::class.java)

        m.checking(object : Expectations() {
            init {
                oneOf(transferManager).downloadDirectory(
                    getBucketName(),
                    KEY,
                    destinationDirectory
                )
                will(returnValue(multipleFileDownload))
                oneOf(multipleFileDownload).waitForCompletion()
            }
        })

        s3WorkingDirectoryTransfer.retrieve(KEY, destinationDirectory)
    }

    private fun getBucketName() = "${LambdaConstants.BUCKET_NAME}-$REGION_NAME"

    private fun createClient() = S3WorkingDirectoryTransfer(transferManager)


    companion object {
        private const val REGION_NAME = "regionName"
        private const val KEY = "key"
    }
}