package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.runtime.Context
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.*


class MyDetachedBuildApi(
    runDetails: RunDetails,
    context: Context,
    engine: HttpClientEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) :
    DetachedBuildApi {

    private val client = HttpClient(engine) {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    context.logger.log("$message\n")
                }
            }
        }
        install(Auth) {
            basic {
                sendWithoutRequest {
                    it.url.build().toString().startsWith(runDetails.teamcityServerUrl)
                }

                credentials {
                    BasicAuthCredentials(runDetails.username, runDetails.password)
                }
            }

        }
    }

    private val teamcityBuildRestApi =
        "${runDetails.teamcityServerUrl}/app/rest/builds/id:${runDetails.buildId}"

    private fun escapeValue(value: String) = value
        .replace("|", "||")
        .replace("'", "|'")
        .replace("[", "|[")
        .replace("]", "|]")
        .replace("\n", "|\n")

    internal fun getServiceMessage(messageType: String, params: Map<String, String>): String {
        val stringBuilder = StringBuilder("##teamcity[$messageType")

        params.forEach { (key, value) -> stringBuilder.append(" $key='${escapeValue(value)}'") }

        stringBuilder.append("]")

        return stringBuilder.toString()
    }

    override fun logAsync(serviceMessage: String?) =
        CoroutineScope(dispatcher).async {
            serviceMessage?.let {
                client.post<Any>("$teamcityBuildRestApi/log") {
                    body = TextContent(serviceMessage, ContentType.Text.Plain)
                }
            }
        }

    override fun logWarningAsync(message: String?): Deferred<Any?> = logAsync(
        getServiceMessage(
            "message",
            mapOf(
                Pair("text", message ?: ""),
                Pair("status", "WARNING")
            )
        )
    )


    override suspend fun finishBuild() {
        client.put<Any>("$teamcityBuildRestApi/finish")
    }

    override fun failBuildAsync(exception: Throwable, errorId: String?): Deferred<Any?> =
        CoroutineScope(dispatcher).async {
            val params = mutableMapOf(
                Pair("description", exception.localizedMessage),
            )
            errorId?.let { params["identity"] = it }
            logAsync(getServiceMessage("buildProblem", params)).await()
        }
}