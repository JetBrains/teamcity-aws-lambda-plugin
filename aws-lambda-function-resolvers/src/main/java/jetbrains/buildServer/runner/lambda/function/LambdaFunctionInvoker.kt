package jetbrains.buildServer.runner.lambda.function

import jetbrains.buildServer.runner.lambda.model.RunDetails

interface LambdaFunctionInvoker {
    fun invokeLambdaFunction(runDetails: List<RunDetails>): Boolean
}