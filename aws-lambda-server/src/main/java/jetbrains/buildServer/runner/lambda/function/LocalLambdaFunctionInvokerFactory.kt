package jetbrains.buildServer.runner.lambda.function

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.clouds.amazon.connector.featureDevelopment.AwsConnectionsManager
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.runner.lambda.aws.ServerAWSConnectionAwsClientFetcher
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.directory.S3WorkingDirectoryTransferImpl
import jetbrains.buildServer.serverSide.SProject
import java.util.concurrent.atomic.AtomicBoolean

class LocalLambdaFunctionInvokerFactory(
        private val awsConnectionsManager: AwsConnectionsManager
) : LambdaFunctionInvokerFactory {
    private val logger = Loggers.CLOUD
    val objectMapper by lazy {
        jacksonObjectMapper()
    }

    override fun getLambdaFunctionInvoker(properties: Map<String, String>, project: SProject): LambdaFunctionInvoker {
        val clientFetcher = ServerAWSConnectionAwsClientFetcher(awsConnectionsManager, properties, project)
        val awsLambda = clientFetcher.getAWSLambdaClient()
        val logger = object : Logger {
            override fun message(message: String?) {
                logger.debug(message)
            }
        }

        return LocalLambdaFunctionInvoker(
                logger,
                objectMapper,
                AtomicBoolean(),
                awsLambda,
                LambdaFunctionResolverFactoryImpl(
                        logger,
                        awsLambda,
                        S3WorkingDirectoryTransferImpl(
                                logger,
                                clientFetcher.getTransferManager()
                        ),
                        properties)
        )
    }
}