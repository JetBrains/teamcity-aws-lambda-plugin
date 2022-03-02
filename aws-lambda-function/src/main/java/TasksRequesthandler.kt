import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler

class TasksRequesthandler: RequestHandler<Any, String> {
    override fun handleRequest(input: Any?, context: Context?): String {
        TODO("Not yet implemented")
    }
}