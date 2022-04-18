package jetbrains.buildServer.runner.lambda.function

import jetbrains.buildServer.runner.lambda.RunDetails

interface LambdaFunctionInvoker {
    fun invokeLambdaFunction(runDetails: RunDetails): Boolean
}