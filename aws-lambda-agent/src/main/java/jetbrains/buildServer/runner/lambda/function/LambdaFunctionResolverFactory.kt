package jetbrains.buildServer.runner.lambda.function

interface LambdaFunctionResolverFactory {
    fun getLambdaFunctionResolver(): LambdaFunctionResolver
}