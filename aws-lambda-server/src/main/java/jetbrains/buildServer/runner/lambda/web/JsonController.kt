package jetbrains.buildServer.runner.lambda.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.controllers.AuthorizationInterceptor
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.RequestPermissionsCheckerEx
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil
import jetbrains.buildServer.runner.lambda.LambdaConstants
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.auth.AccessChecker
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.servlet.ModelAndView
import java.io.OutputStreamWriter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

data class JsonControllerException(override val message: String = "", val status: HttpStatus) : Exception(message)

abstract class JsonController<T : Any>(
        descriptor: PluginDescriptor,
        controllerManager: WebControllerManager,
        authInterceptor: AuthorizationInterceptor,
        private val projectManager: ProjectManager,
        private val accessManager: AccessChecker,
        path: String,
        private val allowedMethods: Set<String>,
) : BaseController(), RequestPermissionsCheckerEx {
    private val objectMapper = jacksonObjectMapper()

    init {
        val pluginResourcesPath = descriptor.getPluginResourcesPath(path)
        authInterceptor.addPathBasedPermissionsChecker(pluginResourcesPath, this)
        controllerManager.registerController(pluginResourcesPath, this)
    }

    private fun writeError(
            response: HttpServletResponse,
            errorMessage: String?,
            httpStatus: HttpStatus
    ): ModelAndView? {
        val writer = OutputStreamWriter(response.outputStream)
        response.apply {
            status = httpStatus.value()
            contentType = MediaType.TEXT_PLAIN_VALUE
        }

        errorMessage?.let { writer.write(it) }
        writer.flush()
        return null
    }

    private fun writeMessage(response: HttpServletResponse, message: String, contentType: String): ModelAndView? {
        val writer = OutputStreamWriter(response.outputStream)
        response.contentType = contentType
        writer.write(message)
        writer.flush()
        return null
    }

    private fun error405(response: HttpServletResponse) =
            writeError(response, null, HttpStatus.METHOD_NOT_ALLOWED)

    private fun error404(response: HttpServletResponse, errorMessage: String) =
            writeError(response, errorMessage, HttpStatus.NOT_FOUND)

    private fun error400(response: HttpServletResponse, errorMessage: String) =
            writeError(response, errorMessage, HttpStatus.BAD_REQUEST)

    private fun writeJson(response: HttpServletResponse, data: Any) =
            writeMessage(response, objectMapper.writeValueAsString(data), MediaType.APPLICATION_JSON_VALUE)

    abstract fun handle(project: SProject, request: HttpServletRequest, properties: Map<String, String>): T

    override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
        return handle(request, response)
    }

    internal fun handle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {

        return try {
            val project = getProject(request)

            val bean = BasePropertiesBean(null)
            PluginPropertiesUtil.bindPropertiesFromRequest(request, bean)
            val data = handle(project, request, bean.properties)
            writeJson(response, data)
        } catch (controllerException: JsonControllerException) {
            writeError(response, controllerException.message, controllerException.status)
        }
    }

    protected fun getProject(request: HttpServletRequest): SProject {
        if (!allowedMethods.contains(request.method)) {
            throw JsonControllerException(status = HttpStatus.METHOD_NOT_ALLOWED)
        }

        val settingsId =
                request.getParameter(LambdaConstants.BUILD_TYPE_ID) ?: throw JsonControllerException("Missing parameter ${LambdaConstants.BUILD_TYPE_ID}", HttpStatus.BAD_REQUEST)

        val buildTypeId = if (settingsId.startsWith(BUILD_TYPE_PREFIX)) {
            settingsId.substring(BUILD_TYPE_PREFIX.length)
        } else {
            settingsId
        }

        val buildType = projectManager.findBuildTypeByExternalId(buildTypeId)
                ?: throw JsonControllerException("Build Type $buildTypeId not found", HttpStatus.NOT_FOUND)

        val projectId = buildType.projectId
        val project = projectManager.findProjectById(projectId)
                ?: throw JsonControllerException("Project for Build Type $buildType not found", HttpStatus.NOT_FOUND)
        return project
    }

    companion object {
        internal const val BUILD_TYPE_PREFIX = "buildType:"
    }
}