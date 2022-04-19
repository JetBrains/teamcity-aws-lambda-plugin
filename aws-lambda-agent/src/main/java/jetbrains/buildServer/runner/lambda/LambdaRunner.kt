package jetbrains.buildServer.runner.lambda

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.runner.lambda.LambdaConstants.RUNNER_TYPE
import jetbrains.buildServer.runner.lambda.aws.AWSConnectionAwsClientFetcher
import jetbrains.buildServer.runner.lambda.cmd.UnixCommandLinePreparer
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransferImpl
import jetbrains.buildServer.runner.lambda.directory.TarArchiveManager
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionResolverImpl
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionResolverFactoryImpl
import jetbrains.buildServer.runner.lambda.function.LocalLambdaFunctionInvoker
import jetbrains.buildServer.util.amazon.AWSCommonParams.getCredentialsProvider
import jetbrains.buildServer.util.amazon.AWSCommonParams.withAWSClients
import java.util.concurrent.atomic.AtomicBoolean

class LambdaRunner : AgentBuildRunner {
    override fun createBuildProcess(runningBuild: AgentRunningBuild, context: BuildRunnerContext): BuildProcess {
        val awsClientFetcher = AWSConnectionAwsClientFetcher(context)
        val awsLambda = awsClientFetcher.getAWSLambdaClient()
        val logger = runningBuild.buildLogger
        val genericLogger = object : Logger {
            override fun message(message: String?) {
                logger.message(message)
            }
        }

        val workingDirectoryTransfer = S3WorkingDirectoryTransferImpl(genericLogger, awsClientFetcher.getTransferManager())
        val myIsInterrupted = AtomicBoolean()
        return LambdaBuildProcess(
                context,
                logger,
                workingDirectoryTransfer,
                UnixCommandLinePreparer(context, logger),
                TarArchiveManager(genericLogger),
                LocalLambdaFunctionInvoker(
                        logger, jacksonObjectMapper(), myIsInterrupted, awsLambda,
                        LambdaFunctionResolverFactoryImpl(
                                context,
                                genericLogger,
                                awsLambda,
                                workingDirectoryTransfer,
                        ),
                ),
                myIsInterrupted
        )
    }

    override fun getRunnerInfo(): AgentBuildRunnerInfo = object : AgentBuildRunnerInfo {
        override fun getType(): String = RUNNER_TYPE

        override fun canRun(agentConfiguration: BuildAgentConfiguration): Boolean = true
    }
}
