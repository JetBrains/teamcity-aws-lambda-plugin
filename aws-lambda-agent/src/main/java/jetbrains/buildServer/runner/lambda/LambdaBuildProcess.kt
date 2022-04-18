package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.InvocationType
import com.amazonaws.services.lambda.model.InvokeRequest
import com.fasterxml.jackson.databind.ObjectMapper
import jetbrains.buildServer.agent.BuildFinishedStatus
import jetbrains.buildServer.agent.BuildProcess
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.cmd.CommandLinePreparer
import jetbrains.buildServer.runner.lambda.directory.ArchiveManager
import jetbrains.buildServer.runner.lambda.directory.WorkingDirectoryTransfer
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionResolverFactory
import java.util.concurrent.atomic.AtomicBoolean

class LambdaBuildProcess(
        private val context: BuildRunnerContext,
        private val logger: BuildProgressLogger,
        private val awsLambda: AWSLambda,
        private val objectMapper: ObjectMapper,
        private val workingDirectoryTransfer: WorkingDirectoryTransfer,
        private val commandLinePreparer: CommandLinePreparer,
        private val lambdaFunctionResolverFactoryImpl: LambdaFunctionResolverFactory,
        private val archiveManager: ArchiveManager
) :
        BuildProcess {

    private val myIsInterrupted: AtomicBoolean = AtomicBoolean()
    private val myIsFinished: AtomicBoolean = AtomicBoolean()

    private fun executeTask(): BuildFinishedStatus {
        val projectName = context.buildParameters.systemProperties.getValue(LambdaConstants.TEAMCITY_PROJECT_NAME)
        val scriptContentFilename = commandLinePreparer.writeBuildScriptContent(projectName, context.workingDirectory)
        val key = getKey()

        val workingDirectoryTar = archiveManager.archiveDirectory(context.workingDirectory)
        val directoryId = workingDirectoryTransfer.upload(key, workingDirectoryTar)

        val runDetails = getRunDetails(directoryId, scriptContentFilename)
        val functionName = lambdaFunctionResolverFactoryImpl.getLambdaFunctionResolver().resolveFunction()

        logger.message("Creating request for lambda function")
        val invokeRequest = InvokeRequest()
                .withFunctionName(functionName)
                .withInvocationType(InvocationType.Event)
                .withPayload(objectMapper.writeValueAsString(runDetails))

        if (isInterrupted) {
            return BuildFinishedStatus.INTERRUPTED
        }

        logger.message("Adding request to event queue")
        awsLambda.invoke(invokeRequest)
        myIsFinished.set(true)
        return BuildFinishedStatus.FINISHED_DETACHED
    }

    private fun getKey(): String =
            "${context.build.buildTypeId}-${context.build.buildId}"

    private fun getRunDetails(directoryId: String, scriptContentFilename: String): RunDetails = RunDetails(
            username = context.buildParameters.allParameters.getValue(LambdaConstants.USERNAME_SYSTEM_PROPERTY),
            password = context.buildParameters.allParameters.getValue(LambdaConstants.PASSWORD_SYSTEM_PROPERTY),
            buildId = context.configParameters.getValue(LambdaConstants.TEAMCITY_BUILD_ID),
            teamcityServerUrl = context.configParameters.getValue(LambdaConstants.TEAMCITY_SERVER_URL),
            customScriptFilename = scriptContentFilename,
            directoryId = directoryId
    )

    override fun start() {}

    override fun isInterrupted(): Boolean = myIsInterrupted.get()

    override fun isFinished(): Boolean = myIsFinished.get()

    override fun interrupt() {
        myIsInterrupted.set(true)
    }

    override fun waitFor(): BuildFinishedStatus = when {
        isFinished -> BuildFinishedStatus.FINISHED_DETACHED
        isInterrupted -> BuildFinishedStatus.INTERRUPTED
        else -> executeTask()
    }
}