FROM public.ecr.aws/lambda/java:11

COPY aws-lambda-function/build/classes/kotlin/main ${LAMBDA_TASK_ROOT}
COPY aws-lambda-function/build/dependency/* ${LAMBDA_TASK_ROOT}/lib/

CMD [ "jetbrains.buildServer.runner.lambda.TasksRequestHandler::handleRequest" ]
