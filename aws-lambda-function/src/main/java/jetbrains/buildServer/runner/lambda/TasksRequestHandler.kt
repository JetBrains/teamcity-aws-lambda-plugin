package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream

class TasksRequestHandler : RequestStreamHandler {
    private val objectMapper = jacksonObjectMapper()

    override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
        val runDetails: RunDetails = objectMapper.readValue(input)

        Thread.sleep(5000)

        val detachedBuildApi = MyDetachedBuildApi(runDetails, context)

        runBlocking {
            detachedBuildApi.finishBuild()
        }
    }
}