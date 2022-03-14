package jetbrains.buildServer.runner.lambda

object LambdaConstants {
    const val RUNNER_TYPE = "aws.lambda"
    const val RUNNER_DISPLAY_NAME = "AWS Lambda"
    const val RUNNER_DESCR = "Run your Build Task using AWS Lambda"


    const val FUNCTION_NAME = "TeamcityLambdaRunner"
    const val BUCKET_NAME = "teamcity-lambda-runner-bucket"
    const val EDIT_PARAMS_JSP = "editLambdaParams.jsp"
    const val EDIT_PARAMS_HTML = "editLambdaParams.html"
    const val VIEW_PARAMS_JSP = "viewLambdaParams.jsp"
    const val VIEW_PARAMS_HTML = "viewLambdaParams.html"


    const val USERNAME_SYSTEM_PROPERTY = "system.teamcity.auth.userId"
    const val PASSWORD_SYSTEM_PROPERTY = "system.teamcity.auth.password"
    const val TEAMCITY_SERVER_URL = "teamcity.serverUrl"
    const val TEAMCITY_BUILD_ID = "teamcity.build.id"
    const val TEAMCITY_PROJECT_NAME = "teamcity.projectName"
    const val TEAMCITY_VERSION = "teamcity.version"


    const val LAMBDA_SETTINGS_STEP = "lambda_settings_settings"
    const val LAMBDA_ENDPOINT_URL_PARAM = "lambda.endpoint_url"
    const val LAMBDA_ENDPOINT_URL_LABEL = "Lambda Service Endpoint URL"
    const val LAMBDA_ENDPOINT_URL_NOTE =
        "Should your lambda function be executed through a different service endpoint URL than the default AWS one"
    const val LAMBDA_ENDPOINT_URL_ERROR = "$LAMBDA_ENDPOINT_URL_LABEL does not contain a valid URL"

    const val SCRIPT_CONTENT_PARAM = "lambda.script.content"
    const val SCRIPT_CONTENT_LABEL = "Custom Script"
    const val SCRIPT_CONTENT_NOTE =
        "A Unix-like script, which will be executed as a shell script in a Unix-like environment."
    const val SCRIPT_CONTENT_ERROR = "Script content must be specified"
    const val SCRIPT_CONTENT_FILENAME = "teamcity-lambda-execution.sh"
    const val SCRIPT_CONTENT_CHANGE_DIRECTORY_PREFIX =
        "directory=\$(echo \$(dirname \$0) | cut -d ' ' -f1);cd \${directory[0]};\n"

    const val ECR_IMAGE_URI_PARAM = "lambda.ecr.image.uri"
    const val ECR_IMAGE_URI_LABEL = "ECR Docker Image Uri"
    const val ECR_IMAGE_URI_NOTE =
        "The location of the container image to use for your function. Must depend on the lambda function runner logic provided by JetBrains. Must be available through a private repository on the AWS Account"
    const val FUNCTION_HANDLER = "jetbrains.buildServer.runner.lambda.TasksRequestHandler::handleRequest"

    const val MEMORY_SIZE_PARAM = "lambda.memory.size"
    const val MEMORY_SIZE_LABEL = "Lambda Memory Size"
    const val MEMORY_SIZE_NOTE =
        "Lambda memory size. Must be an integer value between 128MB and 10,240MB"
    const val MEMORY_SIZE_ERROR = "Memory size must be specified"
    const val MIN_MEMORY_SIZE = 128
    const val MAX_MEMORY_SIZE = 10240
    const val MEMORY_SIZE_VALUE_ERROR = "Memory size must be an integer value between ${MIN_MEMORY_SIZE}MB and ${MAX_MEMORY_SIZE}MB"

    const val MAX_TIMEOUT = 900

    const val JETBRAINS_AWS_ACCOUNT_ID = "913206223978"
    const val DEFAULT_LAMBDA_RUNTIME = "java11"
    const val S3_CODE_FUNCTION_URL = "https://teamcity-lambda-runner.s3.eu-central-1.amazonaws.com/aws-lambda-function-all.jar"
    const val LAMBDA_ARN_NAME = "teamcity-lambda-runner"
    const val IAM_PREFIX = "arn:aws:iam"
}