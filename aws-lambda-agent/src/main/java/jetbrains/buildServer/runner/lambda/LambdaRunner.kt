package jetbrains.buildServer.runner.lambda

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.AWSLambdaClientBuilder
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.runner.lambda.LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM
import jetbrains.buildServer.runner.lambda.LambdaConstants.RUNNER_TYPE
import jetbrains.buildServer.runner.lambda.cmd.UnixCommandLinePreparer
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransfer
import jetbrains.buildServer.runner.lambda.directory.TarArchiveManager
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionResolverImpl
import jetbrains.buildServer.runner.lambda.function.ZipFunctionDownloader
import jetbrains.buildServer.util.amazon.AWSCommonParams.getCredentialsProvider
import jetbrains.buildServer.util.amazon.AWSCommonParams.withAWSClients

class LambdaRunner : AgentBuildRunner {
    override fun createBuildProcess(runningBuild: AgentRunningBuild, context: BuildRunnerContext): BuildProcess {
        val awsLambda = getLambdaClient(context)
        return LambdaBuildProcess(
            context,
            awsLambda,
            jacksonObjectMapper(),
            S3WorkingDirectoryTransfer(getTransferManager(context), TarArchiveManager()),
            UnixCommandLinePreparer(context),
            LambdaFunctionResolverImpl(
                context,
                awsLambda,
                getIamClient(context),
                ZipFunctionDownloader(LambdaConstants.S3_CODE_FUNCTION_URL)
            )
        )
    }

    private fun getLambdaClient(context: BuildRunnerContext) =
        withAWSClients<AWSLambda, Exception>(context.runnerParameters) { clients ->
            val clientBuilder = AWSLambdaClientBuilder.standard()
                .withClientConfiguration(clients.clientConfiguration)
                .withCredentials(getCredentialsProvider(context.runnerParameters))

            if (context.runnerParameters.containsKey(LAMBDA_ENDPOINT_URL_PARAM)) {
                clientBuilder.withEndpointConfiguration(
                    AwsClientBuilder.EndpointConfiguration(
                        context.runnerParameters[LAMBDA_ENDPOINT_URL_PARAM],
                        clients.region
                    )
                )
            } else {
                clientBuilder.withRegion(clients.region)
            }

            clientBuilder.build()
        }

    private fun getTransferManager(context: BuildRunnerContext) =
        withAWSClients<TransferManager, Exception>(context.runnerParameters) { clients ->
            TransferManagerBuilder.standard()
                .withS3Client(clients.createS3Client())
                .build()
        }

    private fun getIamClient(context: BuildRunnerContext) =
        withAWSClients<AmazonIdentityManagement, Exception>(context.runnerParameters) { clients ->
            AmazonIdentityManagementClientBuilder.standard()
                .withClientConfiguration(clients.clientConfiguration)
                .withCredentials(getCredentialsProvider(context.runnerParameters))
                .build()
        }


    override fun getRunnerInfo(): AgentBuildRunnerInfo = object : AgentBuildRunnerInfo {
        override fun getType(): String = RUNNER_TYPE

        override fun canRun(agentConfiguration: BuildAgentConfiguration): Boolean = true
    }
}
