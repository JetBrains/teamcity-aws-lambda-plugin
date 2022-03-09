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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async


class MyDetachedBuildApi(runDetails: RunDetails, context: Context, engine: HttpClientEngine) :
    DetachedBuildApi {

    private val client = HttpClient(engine) {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    context.logger.log(message)
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

    override fun logAsync(serviceMessage: String?) =
        CoroutineScope(Dispatchers.IO).async {
            serviceMessage?.let {
                client.post<Any>("$teamcityBuildRestApi/log") {
                    body = TextContent(serviceMessage, ContentType.Text.Plain)
                }
            }
        }


    override suspend fun finishBuild() {
        client.put<Any>("$teamcityBuildRestApi/finish")
    }
}