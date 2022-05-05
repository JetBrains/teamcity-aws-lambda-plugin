package jetbrains.buildServer.runner.lambda

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.engine.cio.*
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.runner.lambda.LambdaConstants.RUNNER_TYPE
import jetbrains.buildServer.runner.lambda.aws.AgentAWSConnectionAwsClientFetcher
import jetbrains.buildServer.runner.lambda.aws.RemoteLambdaFunctionInvoker
import jetbrains.buildServer.runner.lambda.cmd.UnixCommandLinePreparerImpl
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransferImpl
import jetbrains.buildServer.runner.lambda.directory.TarArchiveManager
import java.util.concurrent.atomic.AtomicBoolean

class LambdaRunner : AgentBuildRunner {
    override fun createBuildProcess(runningBuild: AgentRunningBuild, context: BuildRunnerContext): BuildProcess {
        val awsClientFetcher = AgentAWSConnectionAwsClientFetcher(context.runnerParameters)
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
                UnixCommandLinePreparerImpl(context, logger),
                TarArchiveManager(genericLogger),
                RemoteLambdaFunctionInvoker(genericLogger, context, jacksonObjectMapper(), CIO.create()),
                myIsInterrupted
        )
    }


    override fun getRunnerInfo(): AgentBuildRunnerInfo = object : AgentBuildRunnerInfo {
        override fun getType(): String = RUNNER_TYPE

        override fun canRun(agentConfiguration: BuildAgentConfiguration): Boolean = true
    }
}
