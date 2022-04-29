package jetbrains.buildServer.runner.lambda.aws

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.directory.Logger
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionInvoker
import jetbrains.buildServer.runner.lambda.model.RunDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class RemoteLambdaFunctionInvoker(
        private val logger: Logger,
        private val context: BuildRunnerContext,
        private val objectMapper: ObjectMapper,
        engine: HttpClientEngine) : LambdaFunctionInvoker {
    private val teamcityServerUrl = context.configParameters.getValue(LambdaConstants.TEAMCITY_SERVER_URL)
    private val buildTypeId = context.buildParameters.allParameters.getValue(LambdaConstants.BUILD_TYPE_SYSTEM_PROPERTY)
    private val agentUsername = context.buildParameters.allParameters.getValue(LambdaConstants.USERNAME_SYSTEM_PROPERTY)
    private val agentPassword = context.buildParameters.allParameters.getValue(LambdaConstants.PASSWORD_SYSTEM_PROPERTY)
    private val agentName = context.build.agentConfiguration.name
    private val buildId = agentUsername.substring(TEAMCITY_BUILD_ID.length)

    private val client = HttpClient(engine) {
        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    this@RemoteLambdaFunctionInvoker.logger.message(message)
                }
            }
        }
        install(Auth) {
            basic {
                sendWithoutRequest {
                    it.url.build().toString().startsWith(teamcityServerUrl)
                }

                credentials {
                    BasicAuthCredentials(agentUsername, agentPassword)
                }
            }
        }
        install(HttpRequestRetry) {
            maxRetries = 5
            exponentialDelay()
            retryIf { _, response -> response.status == HttpStatusCode.RequestTimeout || response.status == HttpStatusCode.GatewayTimeout }
        }
        install(ContentNegotiation) {
            jackson()
        }
    }

    override fun invokeLambdaFunction(runDetails: RunDetails): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: HttpResponse = client.post("$teamcityServerUrl${LambdaConstants.LAMBDA_PLUGIN_PATH}/${LambdaConstants.INVOKE_LAMBDA_PATH}") {
                    setBody(FormDataContent(Parameters.build {
                        append(LambdaConstants.BUILD_TYPE_ID, buildTypeId)
                        append(LambdaConstants.RUN_DETAILS, objectMapper.writeValueAsString(runDetails))
                        append(LambdaConstants.AGENT_NAME, agentName)
                        append(LambdaConstants.BUILD_ID, buildId)
                        context.runnerParameters.map { (key, value) -> append("$PROPS_PREFIX$key", value) }
                    }))
                }

                if (!response.status.isSuccess()) {
                    logger.message(getErrorMessage(response))
                }
            } catch (e: ResponseException) {
                logger.message(getErrorMessage(e.response))
            }
        }

        return false
    }

    private suspend fun getErrorMessage(response: HttpResponse) =
            "Request to $teamcityServerUrl failed with status ${response.status}: ${response.bodyAsText()}"

    companion object {
        private const val TEAMCITY_BUILD_ID = "TeamCityBuildId="
        const val PROPS_PREFIX = "prop:"
    }
}