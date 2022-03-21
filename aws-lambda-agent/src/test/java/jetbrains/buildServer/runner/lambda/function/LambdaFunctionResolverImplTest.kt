package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.*
import com.amazonaws.services.identitymanagement.model.GetPolicyRequest
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.*
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.jmock.lib.legacy.ClassImposteriser
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.nio.ByteBuffer

class LambdaFunctionResolverImplTest: BaseTestCase(){
    private lateinit var m: Mockery
    private lateinit var context: BuildRunnerContext
    private lateinit var awsLambda: AWSLambda
    private lateinit var iam: AmazonIdentityManagement
    private lateinit var userResult: GetUserResult
    private lateinit var user: User
    private lateinit var awsLambdaException: AWSLambdaException
    private lateinit var resourceNotFoundException: ResourceNotFoundException
    private lateinit var functionDownloader: FunctionDownloader

    @BeforeMethod
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()
        m = Mockery()
        m.setImposteriser(ClassImposteriser.INSTANCE)
        m.setThreadingPolicy(Synchroniser())
        context = m.mock(BuildRunnerContext::class.java)
        awsLambda = m.mock(AWSLambda::class.java)
        iam = m.mock(AmazonIdentityManagement::class.java)
        userResult = m.mock(GetUserResult::class.java)
        user = m.mock(User::class.java)
        awsLambdaException = m.mock(AWSLambdaException::class.java)
        resourceNotFoundException = m.mock(ResourceNotFoundException::class.java)
        functionDownloader = m.mock(FunctionDownloader::class.java)
    }

    private fun mockIamLogic() {
        m.checking(object : Expectations() {
            init {
                oneOf(iam).user
                will(returnValue(userResult))
                oneOf(userResult).user
                will(returnValue(user))
                oneOf(user).arn
                will(returnValue(USER_ARN))
            }
        })
    }

    private fun mockImage(): String {
        val lambdaFunctionName = "${LambdaConstants.FUNCTION_NAME}-$ECR_IMAGE_URI"
        m.checking(object : Expectations(){
            init {
                allowing(context).runnerParameters
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.MEMORY_SIZE_PARAM, MEMORY_SIZE),
                            Pair(LambdaConstants.ECR_IMAGE_URI_PARAM, ECR_IMAGE_URI)
                        )
                    )
                )
            }
        })

        return lambdaFunctionName
    }

    private fun mockDefaultImage(): String{
        val lambdaFunctionName = "${LambdaConstants.FUNCTION_NAME}-${LambdaConstants.DEFAULT_LAMBDA_RUNTIME}"

        m.checking(object : Expectations(){
            init {
                allowing(context).runnerParameters
                will(
                    returnValue(
                        mapOf(
                            Pair(LambdaConstants.MEMORY_SIZE_PARAM, MEMORY_SIZE),
                        )
                    )
                )
            }
        })
        return lambdaFunctionName
    }

    @AfterMethod
    @Throws(Exception::class)
    public override fun tearDown() {
        m.assertIsSatisfied()
        super.tearDown()
    }

    @Test
    fun testResolveFunction(){
        val lambdaFunctionName = mockImage()
        m.checking(object : Expectations(){
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
            }
        })

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DefaultImage(){
        val lambdaFunctionName = mockDefaultImage()

        m.checking(object : Expectations(){
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
            }
        })

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_LocalFunction(){
        val lambdaFunctionName = mockImage()
        m.checking(object : Expectations(){
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(awsLambdaException))
                oneOf(awsLambdaException).fillInStackTrace()
                oneOf(awsLambdaException).statusCode
                will(returnValue(404))
            }
        })

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DefaultImage_LocalFunction(){
        val lambdaFunctionName = mockDefaultImage()

        m.checking(object : Expectations(){
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(awsLambdaException))
                oneOf(awsLambdaException).fillInStackTrace()
                oneOf(awsLambdaException).statusCode
                will(returnValue(404))
            }
        })

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    private fun mockRoleExistingLogic() {
        m.checking(object : Expectations() {
            init {
                oneOf(iam).getPolicy(GetPolicyRequest().apply {
                    policyArn = POLICY_ARN
                })
                oneOf(iam).getRole(GetRoleRequest().apply {
                    roleName = LambdaConstants.LAMBDA_ARN_NAME
                })
            }
        })
    }

    private fun mockAwaitFunctionCreation(lambaFunctionName: String){
        m.checking(object : Expectations(){
            init {
                oneOf(awsLambda).invoke(InvokeRequest().apply {
                    functionName = lambaFunctionName
                })
                will(throwException(awsLambdaException))
                oneOf(awsLambdaException).fillInStackTrace()
                oneOf(awsLambdaException).statusCode
                will(returnValue(500))
            }
        })
    }

    @Test
    fun testResolveFunction_FunctionNotFound(){
        val lambdaFunctionName = mockImage()
        m.checking(object : Expectations(){
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(resourceNotFoundException))
                oneOf(resourceNotFoundException).fillInStackTrace()
            }
        })

        mockIamLogic()
        mockRoleExistingLogic()

        expectCreateFunction(lambdaFunctionName)
        mockAwaitFunctionCreation(lambdaFunctionName)

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    private fun mockRoleNotExistingLogic() {
        m.checking(object : Expectations() {
            init {
                oneOf(iam).getPolicy(GetPolicyRequest().apply {
                    policyArn = POLICY_ARN
                })
                will(throwException(NoSuchEntityException("mock")))
                oneOf(iam).getRole(GetRoleRequest().apply {
                    roleName = LambdaConstants.LAMBDA_ARN_NAME
                })
                will(throwException(NoSuchEntityException("mock")))

                oneOf(iam).createPolicy(CreatePolicyRequest().apply {
                    policyName = LambdaConstants.LAMBDA_ARN_NAME
                    policyDocument  = LambdaFunctionResolver.ARN_POLICY
                })

                oneOf(iam).createRole(CreateRoleRequest().apply {
                    roleName = LambdaConstants.LAMBDA_ARN_NAME
                    assumeRolePolicyDocument = LambdaFunctionResolver.ROLE_POLICY_DOCUMENT
                })

                oneOf(iam).attachRolePolicy(AttachRolePolicyRequest().apply {
                    roleName = LambdaConstants.LAMBDA_ARN_NAME
                    policyArn = POLICY_ARN
                })
            }
        })
    }

    @Test
    fun testResolveFunction_FunctionNotFound_RoleNotFound(){
        val lambdaFunctionName = mockImage()
        m.checking(object : Expectations(){
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(resourceNotFoundException))
                oneOf(resourceNotFoundException).fillInStackTrace()
            }
        })

        mockIamLogic()
        mockRoleNotExistingLogic()

        expectCreateFunction(lambdaFunctionName)
        mockAwaitFunctionCreation(lambdaFunctionName)

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    private fun expectCreateFunction(lambdaFunctionName: String) {
        m.checking(object : Expectations() {
            init {
                oneOf(awsLambda).createFunction(CreateFunctionRequest().apply {
                    functionName = lambdaFunctionName
                    code = FunctionCode().apply {
                        imageUri = ECR_IMAGE_URI
                    }
                    role = ROLE_ARN
                    publish = true
                    packageType = "Image"
                    memorySize = MEMORY_SIZE.toInt()
                    timeout = LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT
                })
            }
        })
    }

    @Test
    fun testResolveFunction_DefaultImage_FunctionNotFound(){
        val lambdaFunctionName = mockDefaultImage()

        m.checking(object : Expectations(){
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(resourceNotFoundException))
                oneOf(resourceNotFoundException).fillInStackTrace()
            }
        })

        mockIamLogic()
        mockRoleExistingLogic()

        expectCreateFunctionWithDefaultImage(lambdaFunctionName)
        mockAwaitFunctionCreation(lambdaFunctionName)

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    @Test
    fun testResolveFunction_DefaultImage_FunctionNotFound_RoleNotFound(){
        val lambdaFunctionName = mockDefaultImage()

        m.checking(object : Expectations(){
            init {
                oneOf(awsLambda).getFunction(GetFunctionRequest().apply {
                    functionName = lambdaFunctionName
                })
                will(throwException(resourceNotFoundException))
                oneOf(resourceNotFoundException).fillInStackTrace()
            }
        })

        mockIamLogic()
        mockRoleNotExistingLogic()

        expectCreateFunctionWithDefaultImage(lambdaFunctionName)
        mockAwaitFunctionCreation(lambdaFunctionName)

        val lambdaFunctionResolve = createClient()
        val functionName = lambdaFunctionResolve.resolveFunction()
        Assert.assertEquals(functionName, lambdaFunctionName)
    }

    private fun expectCreateFunctionWithDefaultImage(lambdaFunctionName: String) {
        m.checking(object : Expectations() {
            init {
                oneOf(functionDownloader).downloadFunctionCode()
                val codeBuffer = ByteBuffer.wrap(ByteArray(0))
                will(returnValue(codeBuffer))
                oneOf(awsLambda).createFunction(CreateFunctionRequest().apply {
                    functionName = lambdaFunctionName
                    code = FunctionCode().apply {
                        zipFile = codeBuffer
                        handler = LambdaConstants.FUNCTION_HANDLER
                    }
                    role = ROLE_ARN
                    publish = true
                    packageType = "Zip"
                    runtime = LambdaConstants.DEFAULT_LAMBDA_RUNTIME
                    memorySize = MEMORY_SIZE.toInt()
                    timeout = LambdaConstants.LAMBDA_FUNCTION_MAX_TIMEOUT
                })
            }
        })
    }

    private fun createClient() = LambdaFunctionResolverImpl(context, awsLambda, iam, functionDownloader)

    companion object {
        const val MEMORY_SIZE = "512"
        const val ECR_IMAGE_URI = "ecrImageUri"
        const val USER_ARN = "${LambdaConstants.IAM_PREFIX}::accountId:user"
        const val POLICY_ARN = "${LambdaConstants.IAM_PREFIX}::accountId:policy/${LambdaConstants.LAMBDA_ARN_NAME}"
        const val ROLE_ARN = "${LambdaConstants.IAM_PREFIX}::accountId:role/${LambdaConstants.LAMBDA_ARN_NAME}"
    }
}