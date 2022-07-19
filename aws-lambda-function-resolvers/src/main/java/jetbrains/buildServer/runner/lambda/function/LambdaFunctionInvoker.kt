package jetbrains.buildServer.runner.lambda.function

import jetbrains.buildServer.runner.lambda.model.RunDetails
import kotlin.jvm.Throws

interface LambdaFunctionInvoker {
    @Throws(Exception::class)
    fun invokeLambdaFunction(runDetails: List<RunDetails>): Boolean
}