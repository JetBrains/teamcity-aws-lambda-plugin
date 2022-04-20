package jetbrains.buildServer.runner.lambda

import jetbrains.buildServer.ExtensionHolder
import jetbrains.buildServer.serverSide.problems.BaseBuildProblemTypeDetailsProvider
import jetbrains.buildServer.serverSide.problems.BuildProblemTypeDetailsProvider

class LambdaBuildProblemTypes(extensionHolder: ExtensionHolder) {
    init {
        register(LambdaConstants.TIMEOUT_BUILD_PROBLEM_TYPE, "Lambda Function timeout", extensionHolder)
        register(LambdaConstants.LAMBDA_INVOCATION_ERROR, "Lambda Function Invocation Error", extensionHolder)
    }

    private fun register(type: String, description: String, extensionHolder: ExtensionHolder) {
        extensionHolder.registerExtension(
            BuildProblemTypeDetailsProvider::class.java,
            type,
            object : BaseBuildProblemTypeDetailsProvider() {
                override fun getType(): String = type

                override fun getTypeDescription(): String = description
            })
    }
}