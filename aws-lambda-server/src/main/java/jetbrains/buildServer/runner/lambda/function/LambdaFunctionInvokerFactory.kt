package jetbrains.buildServer.runner.lambda.function

import jetbrains.buildServer.serverSide.SProject

interface LambdaFunctionInvokerFactory {
    fun getLambdaFunctionInvoker(properties: Map<String, String>, project: SProject): LambdaFunctionInvoker
}