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
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

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
    private val outputStreamMessageQueue: Queue<String> = ConcurrentLinkedQueue()
    private val errorStreamMessageQueue: Queue<String> = ConcurrentLinkedQueue()
    private var buildHasFinished = false
    private val outputStreamJob = CoroutineScope(Dispatchers.IO).launch {
        while (!buildHasFinished) {
            if (outputStreamMessageQueue.isNotEmpty()) {
                val message = buildMessages(outputStreamMessageQueue)
                logMessage(message).join()
                delay(DELAY_MILLISECONDS)
            }
        }
    }

    private val errorStreamJob = CoroutineScope(Dispatchers.IO).launch {
        while (!buildHasFinished) {
            if (errorStreamMessageQueue.isNotEmpty()) {
                val message = buildMessages(outputStreamMessageQueue)
                val serviceMessage = getServiceMessage(
                    "message",
                    mapOf(
                        Pair("text", message),
                        Pair("status", "WARNING")
                    )
                )
                logMessage(serviceMessage).join()
                delay(DELAY_MILLISECONDS)
            }
        }
    }

    private fun buildMessages(messageQueue: Queue<String>): String {
        val builder = StringBuilder()
        while (messageQueue.isNotEmpty()) {
            builder.append(messageQueue.poll())
        }

        return builder.toString()
    }

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

    override fun log(serviceMessage: String?) {
        serviceMessage?.let {
            outputStreamMessageQueue.add(it)
        }
    }

    private fun logMessage(serviceMessage: String) =
        CoroutineScope(dispatcher).launch {

            client.post<Any>("$teamcityBuildRestApi/log") {
                body = TextContent(serviceMessage, ContentType.Text.Plain)
            }
        }


    override fun logWarning(message: String?) {
        message?.let {
            errorStreamMessageQueue.add(
                it
            )
        }
    }


    override suspend fun finishBuild() {
        buildHasFinished = true
        outputStreamJob.join()
        errorStreamJob.join()
        client.put<Any>("$teamcityBuildRestApi/finish")
    }

    override fun failBuild(exception: Throwable, errorId: String?): Job {
        val descriptionEntry = Pair("description", exception.message ?: exception.toString())
        val params = if (errorId == null) {
            mapOf(
                descriptionEntry
            )
        } else {
            mapOf(
                descriptionEntry,
                Pair("identity", errorId)
            )
        }
        return logMessage(getServiceMessage("buildProblem", params))
    }

    companion object {
        private const val DELAY_MILLISECONDS = 1000L
    }
}

