package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.InvocationType
import com.amazonaws.services.lambda.model.InvokeRequest
import com.fasterxml.jackson.databind.ObjectMapper
import jetbrains.buildServer.runner.lambda.model.RunDetails
import jetbrains.buildServer.runner.lambda.directory.Logger
import java.util.concurrent.atomic.AtomicBoolean

class LocalLambdaFunctionInvoker(
        private val logger: Logger,
        private val objectMapper: ObjectMapper,
        private val isInterrupted: AtomicBoolean,
        private val awsLambda: AWSLambda,
        private val lambdaFunctionResolverFactory: LambdaFunctionResolverFactory) : LambdaFunctionInvoker {
    override fun invokeLambdaFunction(runDetails: List<RunDetails>): Boolean {
        val functionName = lambdaFunctionResolverFactory.getLambdaFunctionResolver().resolveFunction()

        logger.message("Creating request for lambda functions")


        val invokeRequests = runDetails.map {
            InvokeRequest()
                    .withFunctionName(functionName)
                    .withInvocationType(InvocationType.Event)
                    .withPayload(objectMapper.writeValueAsString(it))
        }

        if (isInterrupted.get()) {
            return true
        }

        logger.message("Adding requests to event queue")
        invokeRequests.forEach {
            awsLambda.invoke(it)
        }
        return false
    }
}