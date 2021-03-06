package jetbrains.buildServer.runner.lambda.web

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.controllers.AuthorizationInterceptor
import jetbrains.buildServer.controllers.agent.AgentFinder
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.runner.lambda.function.LambdaFunctionInvokerFactory
import jetbrains.buildServer.runner.lambda.model.RunDetails
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.serverSide.auth.AccessChecker
import jetbrains.buildServer.serverSide.auth.AccessDeniedException
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.http.HttpStatus
import java.util.*
import javax.servlet.http.HttpServletRequest

class InvokeLambdaFunctionController(
        descriptor: PluginDescriptor,
        controllerManager: WebControllerManager,
        projectManager: ProjectManager,
        accessManager: AccessChecker,
        authInterceptor: AuthorizationInterceptor,
        private val runningBuildsManager: RunningBuildsManager,
        private val lambdaFunctionInvokerFactory: LambdaFunctionInvokerFactory,
        private val agentFinder: AgentFinder
) : JsonController<Boolean>(descriptor, controllerManager, authInterceptor, projectManager, accessManager, LambdaConstants.INVOKE_LAMBDA_PATH, ALLOWED_METHODS) {
    val objectMapper by lazy {
        jacksonObjectMapper()
    }

    override fun handle(project: SProject, request: HttpServletRequest, properties: Map<String, String>): Boolean {
        val serializedDetails = request.getParameter(LambdaConstants.RUN_DETAILS) ?: throw JsonControllerException("Parameter missing: ${LambdaConstants.RUN_DETAILS}", HttpStatus.BAD_REQUEST)
        val builId = request.getParameter(LambdaConstants.BUILD_ID)?.toLong()
                ?: throw JsonControllerException("Parameter missing: ${LambdaConstants.BUILD_ID}", HttpStatus.BAD_REQUEST)
        val build = runningBuildsManager.findRunningBuildById(builId) ?: throw JsonControllerException("No build found found: $builId", HttpStatus.BAD_REQUEST)

        return try {
            val runDetails = objectMapper.readValue<List<RunDetails>>(serializedDetails)
            val lambdaFunctionInvoker = lambdaFunctionInvokerFactory
                    .getLambdaFunctionInvoker(properties, project)

            storeNumInvocations(build, runDetails.size)
            lambdaFunctionInvoker.invokeLambdaFunction(runDetails)
        } catch (e: JsonProcessingException) {
            stopBuild(build, e)
            throw JsonControllerException("Error processing ${LambdaConstants.RUN_DETAILS} parameter: ${e.localizedMessage}", HttpStatus.BAD_REQUEST)
        } catch (e: JsonMappingException) {
            stopBuild(build, e)
            throw JsonControllerException("Error mapping ${LambdaConstants.RUN_DETAILS} parameter: ${e.localizedMessage}", HttpStatus.BAD_REQUEST)
        } catch (e: Exception) {
            stopBuild(build, e)
            throw JsonControllerException("Unexpecte error found: ${e.localizedMessage}", HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    private fun storeNumInvocations(build: SRunningBuild, numInvocations: Int) {
        val buildPromotion = build.buildPromotion as BuildPromotionEx

        buildPromotion.setAttribute(LambdaConstants.NUM_INVOCATIONS_PARAM, numInvocations)
        buildPromotion.persist()
    }

    override fun checkPermissions(securityContext: SecurityContextEx, request: HttpServletRequest) {
        agentFinder.findAgent(request)
                ?: throw AccessDeniedException(securityContext.authorityHolder, "Request has not been called from an agent")
    }

    private fun stopBuild(build: SRunningBuild, e: Exception) {
        CoroutineScope(Dispatchers.IO).launch {
            build.addBuildProblem(BuildProblemData.createBuildProblem(
                    e::class.java.simpleName,
                    LambdaConstants.LAMBDA_INVOCATION_ERROR,
                    e.localizedMessage
            ))
            while (!build.isDetachedFromAgent) {
                delay(DELAY_MILLISECONDS)
            }

            build.finish(Date())
        }
    }

    companion object {
        internal const val DELAY_MILLISECONDS = 1000L
        internal val ALLOWED_METHODS = setOf(METHOD_POST)
    }
}