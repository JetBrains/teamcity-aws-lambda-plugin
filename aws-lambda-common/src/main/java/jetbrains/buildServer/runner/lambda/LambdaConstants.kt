package jetbrains.buildServer.runner.lambda

object LambdaConstants {
    const val RUNNER_TYPE = "aws.lambda"
    const val RUNNER_DISPLAY_NAME = "AWS Lambda"
    const val RUNNER_DESCR = "Run your Build Task using AWS Lambda"


    const val FUNCTION_NAME = "TeamcityLambdaRunner"
    const val EDIT_PARAMS_JSP = "editLambdaParams.jsp"
    const val EDIT_PARAMS_HTML = "editLambdaParams.html"
    const val VIEW_PARAMS_JSP = "viewLambdaParams.jsp"
    const val VIEW_PARAMS_HTML = "viewLambdaParams.html"


    const val USERNAME_SYSTEM_PROPERTY = "system.teamcity.auth.userId"
    const val PASSWORD_SYSTEM_PROPERTY = "system.teamcity.auth.password"
    const val TEAMCITY_SERVER_URL = "teamcity.serverUrl"
    const val TEAMCITY_BUILD_ID = "teamcity.build.id"

    const val LAMBDA_SETTINGS_STEP = "lambda_settings_settings"
    const val LAMBDA_ENDPOINT_URL_PARAM = "lambda.endpoint_url"
    const val LAMBDA_ENDPOINT_URL_LABEL = "Lambda Service Endpoint URL"
    const val LAMBDA_ENDPOINT_URL_NOTE = "Should your lambda function be executed through a different service endpoint URL than the default AWS one"
}