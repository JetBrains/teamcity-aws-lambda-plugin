package jetbrains.buildServer.runner.lambda.function

interface LambdaFunctionResolver {
    fun resolveFunction(): String
}