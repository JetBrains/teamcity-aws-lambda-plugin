package jetbrains.buildServer.runner.lambda

import jetbrains.buildServer.runner.lambda.LambdaConstants.PASSWORD_SYSTEM_PROPERTY
import jetbrains.buildServer.runner.lambda.LambdaConstants.TEAMCITY_BUILD_ID
import jetbrains.buildServer.runner.lambda.LambdaConstants.TEAMCITY_SERVER_URL
import jetbrains.buildServer.runner.lambda.LambdaConstants.USERNAME_SYSTEM_PROPERTY

class LambdaCommandParameters(runnerParameters: Map<String, String>) {
    val teamcityUrl = runnerParameters.getValue(TEAMCITY_SERVER_URL)
    val username = runnerParameters.getValue(USERNAME_SYSTEM_PROPERTY)
    val password = runnerParameters.getValue(PASSWORD_SYSTEM_PROPERTY)
    val buildId = runnerParameters.getValue(TEAMCITY_BUILD_ID)


    companion object {

        private const val COMMAND_ARGUMENTS = "command.args"

        private const val WORKING_DIR = "teamcity.build.workingDir"
    }
}