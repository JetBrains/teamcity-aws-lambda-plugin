package jetbrains.buildServer.runner.lambda.web

import jetbrains.buildServer.controllers.AuthorizationInterceptor
import jetbrains.buildServer.controllers.agent.AgentFinder
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.serverSide.auth.AccessChecker
import jetbrains.buildServer.serverSide.auth.AccessDeniedException
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.http.HttpStatus
import java.util.*
import javax.servlet.http.HttpServletRequest

data class InvocationDetails(val numBuilds: Int, val finishedBuilds: Set<Long>)

class FinishLambdaController(
        descriptor: PluginDescriptor,
        controllerManager: WebControllerManager,
        projectManager: ProjectManager,
        accessManager: AccessChecker,
        authInterceptor: AuthorizationInterceptor,
        private val runningBuildsManager: RunningBuildsManager,
        private val agentFinder: AgentFinder) :
        JsonController<Nothing?>(
                descriptor,
                controllerManager,
                authInterceptor,
                projectManager,
                accessManager,
                LambdaConstants.FINISH_LAMBDA_PATH,
                ALLOWED_METHODS) {

    val invocationsCount = mutableMapOf<Long, InvocationDetails>()

    override fun handle(project: SProject, request: HttpServletRequest, properties: Map<String, String>): Nothing? {
        val buildId = request.getParameter(LambdaConstants.BUILD_ID)?.toLong()
                ?: throw JsonControllerException("Parameter missing: ${LambdaConstants.BUILD_ID}", HttpStatus.BAD_REQUEST)
        val invocationId = request.getParameter(LambdaConstants.INVOCATION_ID)?.toLong()
                ?: throw JsonControllerException("Parameter missing: ${LambdaConstants.INVOCATION_ID}", HttpStatus.BAD_REQUEST)
        val build by lazy {
            runningBuildsManager.findRunningBuildById(buildId) ?: throw JsonControllerException("No build $buildId found", HttpStatus.BAD_REQUEST)
        }

        val invocationDetails = invocationsCount.compute(buildId) { _, value ->
            val invocationDetails = value ?: kotlin.run {
                val buildPromotion = build.buildPromotion as BuildPromotionEx
                val numBuilds = buildPromotion.getAttribute(LambdaConstants.NUM_INVOCATIONS_PARAM) as Long?
                        ?: throw JsonControllerException("No number of invocations has been stored", HttpStatus.INTERNAL_SERVER_ERROR)
                InvocationDetails(numBuilds.toInt(), emptySet())
            }

            invocationDetails.copy(finishedBuilds = invocationDetails.finishedBuilds + invocationId)
        }!!

        if (invocationDetails.numBuilds == invocationDetails.finishedBuilds.size) {
            build.finish(Date())
            invocationsCount.remove(buildId)
        }

        return null
    }

    override fun checkPermissions(securityContext: SecurityContextEx, request: HttpServletRequest) {
        agentFinder.findAgent(request)
                ?: throw AccessDeniedException(securityContext.authorityHolder, "Request did not have access to agent credentials")
    }

    companion object {
        internal val ALLOWED_METHODS = setOf(METHOD_POST)
    }
}