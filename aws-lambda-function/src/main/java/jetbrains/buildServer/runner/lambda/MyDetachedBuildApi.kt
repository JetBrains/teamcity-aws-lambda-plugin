package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.runtime.Context
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import jetbrains.buildServer.runner.lambda.model.RunDetails
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class MyDetachedBuildApi(
        private val runDetails: RunDetails,
        context: Context,
        engine: HttpClientEngine,
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : DetachedBuildApi {
    private val logger = context.logger

    private val client = HttpClient(engine) {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    this@MyDetachedBuildApi.logger.log("$message\n")
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
        install(HttpRequestRetry) {
            maxRetries = 5
            exponentialDelay()
            retryIf { _, response -> response.status == HttpStatusCode.RequestTimeout || response.status == HttpStatusCode.GatewayTimeout }
        }
    }

    private val teamcityBuildRestApi = "${runDetails.teamcityServerUrl}/app/rest/builds/id:${runDetails.buildDetails.buildId}"
    private val teamcityFinishLambdaApi = "${runDetails.teamcityServerUrl}${LambdaConstants.LAMBDA_PLUGIN_PATH}/${LambdaConstants.FINISH_LAMBDA_PATH}"
    private val outputStreamMessageQueue: Queue<String> = ConcurrentLinkedQueue()
    private val errorStreamMessageQueue: Queue<String> = ConcurrentLinkedQueue()
    private var buildHasFinished = false
    private val outputStreamJob = CoroutineScope(Dispatchers.IO).launch {
        while (!buildHasFinished) {
            processOutputStream()
        }
        processOutputStream()
    }

    private val errorStreamJob = CoroutineScope(Dispatchers.IO).launch {
        while (!buildHasFinished) {
            processErrorStream()
        }
        processErrorStream()
    }

    private suspend fun processOutputStream() {
        if (outputStreamMessageQueue.isNotEmpty()) {
            val message = buildMessages(outputStreamMessageQueue)
            val serviceMessage = getServiceMessage(
                "message", mapOf(
                    Pair("text", message),
                )
            )

            logMessage(serviceMessage).join()
            delay(DELAY_MILLISECONDS)
        }
    }

    private suspend fun processErrorStream() {
        if (errorStreamMessageQueue.isNotEmpty()) {
            val message = buildMessages(errorStreamMessageQueue)
            val serviceMessage = getServiceMessage(
                "message", mapOf(
                    Pair("text", message), Pair("status", "WARNING")
                )
            )
            logMessage(serviceMessage).join()
            delay(DELAY_MILLISECONDS)
        }
    }

    override suspend fun startLogging() {
        val serviceMessage = getServiceMessage(
            "blockOpened", mapOf(Pair("name", getFlowIdValue()), Pair("description", "AWS Lambda Execution - Run ${runDetails.invocationId}"))
        )
        logMessage(serviceMessage).join()
    }

    private fun getFlowIdValue() = "$FLOW_ID_VALUE - Run ${runDetails.invocationId}"

    override suspend fun stopLogging() {
        val serviceMessage = getServiceMessage("blockClosed", mapOf(Pair("name", getFlowIdValue())))
        logMessage(serviceMessage).join()
    }

    private fun buildMessages(messageQueue: Queue<String>): String {
        val builder = StringBuilder()
        while (messageQueue.isNotEmpty()) {
            builder.append(messageQueue.poll())
        }

        return builder.toString()
    }

    private fun escapeValue(value: String) =
        value.replace("|", "||").replace("'", "|'").replace("[", "|[").replace("]", "|]").replace("\n", "|n")
            .replace("\r", "|r")

    internal fun getServiceMessage(messageType: String, params: Map<String, String>): String {
        val paramsWithFlow = mapOf(
            Pair(FLOW_ID, getFlowIdValue())
        ) + params

        val stringBuilder = StringBuilder("##teamcity[$messageType")

        paramsWithFlow.forEach { (key, value) -> stringBuilder.append(" $key='${escapeValue(value)}'") }

        stringBuilder.append("]")

        val serviceMessage = stringBuilder.toString()

        logger.log("Built service message $serviceMessage")
        return serviceMessage
    }

    override fun log(serviceMessage: String?) {
        serviceMessage?.let {
            outputStreamMessageQueue.add(it)
        }
    }

    private fun logMessage(serviceMessage: String) = CoroutineScope(dispatcher).launch {
        logger.log("Sending message $serviceMessage...\n")
        client.post("$teamcityBuildRestApi/log") {
            setBody(TextContent(serviceMessage, ContentType.Text.Plain))
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
        client.post("$teamcityFinishLambdaApi"){
            setBody(FormDataContent(Parameters.build {
                append(LambdaConstants.BUILD_TYPE_ID, runDetails.buildDetails.buildTypeId)
                append(LambdaConstants.AGENT_NAME, runDetails.buildDetails.agentName)
                append(LambdaConstants.BUILD_ID, runDetails.buildDetails.buildId)
                append(LambdaConstants.INVOCATION_ID, runDetails.invocationId.toString())
            }))
        }
    }

    override fun failBuild(exception: Throwable, errorId: String?): Job {
        val descriptionEntry = Pair("description", exception.message ?: exception.toString())
        val params = if (errorId == null) {
            mapOf(
                descriptionEntry
            )
        } else {
            mapOf(
                descriptionEntry, Pair("identity", errorId)
            )
        }
        return logMessage(getServiceMessage("buildProblem", params))
    }

    companion object {
        private const val DELAY_MILLISECONDS = 1000L
        private const val FLOW_ID = "flowId"
        private const val FLOW_ID_VALUE = "AWS Lambda"
    }
}

