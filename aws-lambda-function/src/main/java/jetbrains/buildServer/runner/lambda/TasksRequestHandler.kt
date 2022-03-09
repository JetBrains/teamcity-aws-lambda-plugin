package jetbrains.buildServer.runner.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.engine.cio.*
import jetbrains.buildServer.runner.lambda.build.LambdaCommandLine
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream

class TasksRequestHandler : RequestStreamHandler {
    private val objectMapper = jacksonObjectMapper()

    override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
        val runDetails: RunDetails = objectMapper.readValue(input)

        output.write("Got teamcityServer ${runDetails.teamcityServerUrl}".toByteArray())
        val detachedBuildApi = MyDetachedBuildApi(runDetails, context, CIO.create())
        val jobs = LambdaCommandLine(runDetails, context.logger).executeCommandLine(detachedBuildApi)

        runBlocking {
            jobs.awaitAll()
            detachedBuildApi.finishBuild()
        }
    }
}