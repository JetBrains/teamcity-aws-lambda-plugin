package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.runtime.Context
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MyDetachedBuildApi(private val runDetails: RunDetails, context: Context) :
    DetachedBuildApi {

    private val client: HttpClient = HttpClient(CIO) {
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

    override fun log(serviceMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            client.post<Any>("$teamcityBuildRestApi/log") {
                headers {
                    contentType(ContentType.Text.Plain)
                }
                body = serviceMessage
            }
        }
    }


    override suspend fun finishBuild() {
        client.put<Any>("$teamcityBuildRestApi/finish")
    }
}