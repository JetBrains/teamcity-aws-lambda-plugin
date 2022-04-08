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
        return LambdaBuildProcess(
            context,
            logger,
            awsLambda,
            jacksonObjectMapper(),
            workingDirectoryTransfer,
            UnixCommandLinePreparer(context, logger),
            LambdaFunctionResolverImpl(
                context,
                logger,
                awsLambda,
                workingDirectoryTransfer,
            ),
            TarArchiveManager(genericLogger)
        )
    }


    override fun getRunnerInfo(): AgentBuildRunnerInfo = object : AgentBuildRunnerInfo {
        override fun getType(): String = RUNNER_TYPE

        override fun canRun(agentConfiguration: BuildAgentConfiguration): Boolean = true
    }
}
