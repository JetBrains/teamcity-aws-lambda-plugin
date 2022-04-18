package jetbrains.buildServer.runner.lambda

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.AWSLambdaClientBuilder
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.runner.lambda.LambdaConstants.LAMBDA_ENDPOINT_URL_PARAM
import jetbrains.buildServer.runner.lambda.LambdaConstants.RUNNER_TYPE
import jetbrains.buildServer.runner.lambda.cmd.UnixCommandLinePreparer
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransferImpl
import jetbrains.buildServer.runner.lambda.directory.TarArchiveManager
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionResolverFactoryImpl
import jetbrains.buildServer.util.amazon.AWSCommonParams.getCredentialsProvider
import jetbrains.buildServer.util.amazon.AWSCommonParams.withAWSClients

class LambdaRunner : AgentBuildRunner {
    override fun createBuildProcess(runningBuild: AgentRunningBuild, context: BuildRunnerContext): BuildProcess {
        val awsLambda = getLambdaClient(context)
        val logger = runningBuild.buildLogger
        val genericLogger = object : Logger {
            override fun message(message: String?) {
                logger.message(message)
            }
        }

        val workingDirectoryTransfer = S3WorkingDirectoryTransferImpl(genericLogger, getTransferManager(context))
        return LambdaBuildProcess(
            context,
            logger,
            awsLambda,
            jacksonObjectMapper(),
            workingDirectoryTransfer,
            UnixCommandLinePreparer(context, logger),
            LambdaFunctionResolverFactoryImpl(
                context,
                logger,
                awsLambda,
                workingDirectoryTransfer,
            ),
            TarArchiveManager(genericLogger)
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


    override fun getRunnerInfo(): AgentBuildRunnerInfo = object : AgentBuildRunnerInfo {
        override fun getType(): String = RUNNER_TYPE

        override fun canRun(agentConfiguration: BuildAgentConfiguration): Boolean = true
    }
}
