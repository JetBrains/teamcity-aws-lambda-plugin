import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import jetbrains.buildServer.runner.lambda.RunDetails

class TasksRequesthandler: RequestHandler<RunDetails, String> {
    override fun handleRequest(input: RunDetails, context: Context): String {
        TODO("Not yet implemented")
    }
}