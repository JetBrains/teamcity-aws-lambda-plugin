package jetbrains.buildServer.runner.lambda.build

data class ProcessFailedException(private val errorMessage: String) : Exception(errorMessage)