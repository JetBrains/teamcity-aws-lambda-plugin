package jetbrains.buildServer.runner.lambda.directory

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.HeadBucketRequest
import com.amazonaws.services.s3.transfer.Download
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.Upload
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
    private lateinit var archiveManager: ArchiveManager
    private lateinit var file: File

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

    private fun verifyDirectoryIsUploaded(workDirectory: File, upload: Upload) {

        m.checking(object : Expectations() {
            init {
                oneOf(archiveManager).archiveDirectory(workDirectory)
                will(returnValue(file))
                oneOf(transferManager).upload(
                    with(getBucketName()),
                    with(any(String::class.java)),
                    with(file)
                )
                will(returnValue(upload))
                oneOf(upload).waitForCompletion()
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

        s3WorkingDirectoryTransfer.upload(workDirectory)
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
        val download = m.mock(Download::class.java)
        val destinationDirectory = m.mock(File::class.java)

        m.checking(object : Expectations() {
            init {
                oneOf(transferManager).download(
                    with(getBucketName()),
                    with(KEY),
                    with(any(File::class.java))
                )
                will(returnValue(download))
                oneOf(download).waitForCompletion()
                oneOf(archiveManager).extractDirectory(with(any(File::class.java)), with(destinationDirectory))
            }
        })

        s3WorkingDirectoryTransfer.retrieve(KEY, destinationDirectory)
    }

    private fun getBucketName() = "${LambdaConstants.BUCKET_NAME}-$REGION_NAME"

    private fun createClient() = S3WorkingDirectoryTransfer(transferManager, archiveManager)


    companion object {
        private const val REGION_NAME = "regionName"
        private const val KEY = "key"
    }
}