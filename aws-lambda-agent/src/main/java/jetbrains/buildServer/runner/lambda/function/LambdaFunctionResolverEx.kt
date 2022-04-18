package jetbrains.buildServer.runner.lambda.function

import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.*
import com.amazonaws.waiters.WaiterParameters
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.runner.lambda.LambdaConstants

abstract class LambdaFunctionResolverEx(
        protected val lambdaMemory: Int,
        protected val iamRole: String,
        protected val lambdaStorage: Int,
        protected val functionImage: String,
        protected val logger: BuildProgressLogger,
        protected val awsLambda: AWSLambda,
        protected val lambdaFunctionName: String) : LambdaFunctionResolver {
    override fun resolveFunction(): String {
        val getFunctionRequest = GetFunctionRequest().apply {
            functionName = lambdaFunctionName
        }

        try {
            logger.message("Checking if function $lambdaFunctionName already exists...")
            val function = awsLambda.getFunction(getFunctionRequest)

            logger.message("Function $lambdaFunctionName already exists, checking if configuration is updated...")
            if (doesFunctionConfigurationNeedUpdate(function)) {
                updateFunctionConfiguration(lambdaFunctionName)
            }

            if (doesFunctionNeedUpdate(function)) {
                updateFunction()
            }
        } catch (e: ResourceNotFoundException) {
            createFunction(lambdaFunctionName)
        } catch (e: AWSLambdaException) {
            // Should the function be executed locally, a 404 will happen
            if (e.statusCode != 404) {
                throw e
            }
        }

        return lambdaFunctionName
    }

    fun createFunction(lambdaFunctionName: String) {
        logger.message("Function $lambdaFunctionName does not exist, creating it...")

        val createFunctionRequest = getFunctionRequest()

        awsLambda.createFunction(createFunctionRequest)
        awaitFunctionStatus()
    }

    abstract fun getFunctionRequest(): CreateFunctionRequest

    abstract fun updateFunction()

    abstract fun doesFunctionNeedUpdate(function: GetFunctionResult): Boolean

    private fun updateFunctionConfiguration(lambdaFunctionName: String) {
        logger.message("Function $lambdaFunctionName's configuration is outdated, updating it")
        val updateFunctionConfigurationRequest = UpdateFunctionConfigurationRequest().apply {
            functionName = lambdaFunctionName
            role = iamRole
            memorySize = lambdaMemory
            ephemeralStorage = EphemeralStorage().apply {
                size = lambdaStorage
            }
        }

        awsLambda.updateFunctionConfiguration(updateFunctionConfigurationRequest)
        awaitFunctionStatus()
    }

    protected fun awaitFunctionStatus() {
        val waiters = awsLambda.waiters()
        waiters.functionActiveV2().run(
                WaiterParameters(
                        GetFunctionRequest().apply {
                            functionName = lambdaFunctionName
                        })
        )

        waiters.functionUpdatedV2().run(
                WaiterParameters(
                        GetFunctionRequest().apply {
                            functionName = lambdaFunctionName
                        })
        )
    }

    private fun doesFunctionConfigurationNeedUpdate(function: GetFunctionResult): Boolean {
        if (function.configuration.memorySize != lambdaMemory) {
            return true
        }
        if (function.configuration.role != iamRole) {
            return true
        }
        if (function.configuration.ephemeralStorage.size != lambdaStorage) {
            return true
        }
        return false
    }
}