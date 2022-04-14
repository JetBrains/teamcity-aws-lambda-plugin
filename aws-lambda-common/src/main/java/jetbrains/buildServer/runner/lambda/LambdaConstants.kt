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
    const val LAMBDA_PLUGIN_PATH = "/plugins/aws-lambda-plugin"
    const val IAM_ROLES_LIST_PATH = "iam/list.html"
    const val IAM_ROLES_CREATE_PATH = "iam.html"

    const val TIMEOUT_BUILD_PROBLEM_TYPE = "LAMBDA_TIMEOUT"

    const val USERNAME_SYSTEM_PROPERTY = "system.teamcity.auth.userId"
    const val PASSWORD_SYSTEM_PROPERTY = "system.teamcity.auth.password"
    const val TEAMCITY_SERVER_URL = "teamcity.serverUrl"
    const val TEAMCITY_BUILD_ID = "teamcity.build.id"
    const val TEAMCITY_PROJECT_NAME = "teamcity.projectName"
    const val TEAMCITY_VERSION = "teamcity.version"


    const val LAMBDA_SETTINGS_STEP = "lambda_settings_settings"
    const val LAMBDA_ENDPOINT_URL_PARAM = "lambda.endpoint_url"
    const val LAMBDA_ENDPOINT_URL_LABEL = "Lambda Service Endpoint URL"
    const val LAMBDA_ENDPOINT_URL_ERROR = "$LAMBDA_ENDPOINT_URL_LABEL does not contain a valid URL"

    const val SCRIPT_CONTENT_PARAM = "lambda.script.content"
    const val SCRIPT_CONTENT_ERROR = "Script content must be specified"
    const val SCRIPT_CONTENT_FILENAME = "teamcity-lambda-execution.sh"
    const val SCRIPT_CONTENT_HEADER = "#!/bin/bash\n"
    const val SCRIPT_CONTENT_CHANGE_DIRECTORY_PREFIX =
        "directory=\$(echo \$(dirname \$0) | cut -d ' ' -f1);cd \${directory[0]};\n"

    const val ECR_IMAGE_URI_PARAM = "lambda.ecr.image.uri"
    const val ECR_IMAGE_URI_LABEL = "ECR Docker Image Uri"
    const val FUNCTION_HANDLER = "jetbrains.buildServer.runner.lambda.TasksRequestHandler::handleRequest"

    const val MEMORY_SIZE_PARAM = "lambda.memory.size"
    const val MEMORY_SIZE_LABEL = "Lambda Memory Size"
    const val MEMORY_SIZE_ERROR = "Memory size must be specified"
    const val MIN_MEMORY_SIZE = 128
    const val MAX_MEMORY_SIZE = 10240
    const val MEMORY_SIZE_VALUE_ERROR = "Memory size must be an integer value between ${MIN_MEMORY_SIZE}MB and ${MAX_MEMORY_SIZE}MB"

    const val STORAGE_SIZE_PARAM = "lambda.storage.size"
    const val STORAGE_SIZE_LABEL = "Lambda Ephemeral Storage Size"
    const val STORAGE_SIZE_ERROR = "Storage size must be specified"
    const val MIN_STORAGE_SIZE = 512
    const val MAX_STORAGE_SIZE = 10240
    const val STORAGE_SIZE_VALUE_ERROR = "Storage size must be an integer value between ${MIN_STORAGE_SIZE}MB and ${MAX_STORAGE_SIZE}MB"

    const val IAM_ROLE_PARAM = "lambda.iam.role.arn"
    const val IAM_ROLE_LABEL = "Lambda IAM Role ARN"
    const val IAM_ROLE_ERROR = "IAM Role must be specified"
    const val IAM_ROLE_INVALID_ERROR = "Specified IAM Role does not exist"
    const val IAM_ROLE_SELECT_OPTION = "-- Select IAM Role --"

    const val LAMBDA_FUNCTION_MAX_TIMEOUT = 900
    const val LAMBDA_FUNCTION_MAX_TIMEOUT_LEEWAY = 300
    const val S3_URL_TIMEOUT_MINUTES = 5

    const val AWS_LAMBDA_BASIC_EXECUTION_ROLE_POLICY = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
    const val DEFAULT_LAMBDA_RUNTIME = "java11"
    const val DEFAULT_LAMBDA_ARN_NAME = "teamcity-lambda-runner"
    const val IAM_PREFIX = "arn:aws:iam"
    const val FILE_PREFIX = "teamcity-"
}