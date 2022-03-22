package jetbrains.buildServer.runner.lambda.function

interface LambdaFunctionResolver {
    fun resolveFunction(): String

    companion object {
        const val ROLE_POLICY_DOCUMENT = "{\n" +
                "    \"Version\": \"2012-10-17\",\n" +
                "    \"Statement\": [\n" +
                "        {\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Principal\": {\n" +
                "                \"Service\": \"lambda.amazonaws.com\"\n" +
                "            },\n" +
                "            \"Action\": \"sts:AssumeRole\"\n" +
                "        }\n" +
                "    ]\n" +
                "}"
    }
}