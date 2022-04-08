package jetbrains.buildServer.runner.lambda.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil
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

data class JsonControllerException(override val message: String, val status: HttpStatus) : Exception(message)

abstract class JsonController<T : Any>(
    descriptor: PluginDescriptor,
    controllerManager: WebControllerManager,
    private val projectManager: ProjectManager,
    private val accessManager: AccessChecker,
    path: String,
    private val allowedMethods: Set<String>
) : BaseController() {
    private val objectMapper = jacksonObjectMapper()

    init {
        val pluginResourcesPath = descriptor.getPluginResourcesPath(path)
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
        if (!allowedMethods.contains(request.method)) {
            return error405(response)
        }

        val settingsId =
            request.getParameter(BUILD_TYPE_ID) ?: return error400(response, "Missing parameter $BUILD_TYPE_ID")

        val buildTypeId = settingsId.substring(BUILD_TYPE_PREFIX.length)
        val buildType = projectManager.findBuildTypeByExternalId(buildTypeId)
            ?: return error404(response, "Build Type $buildTypeId not found")

        val projectId = buildType.projectId
        val project = projectManager.findProjectById(projectId)
            ?: return error404(response, "Project for Build Type $buildType not found")

        accessManager.checkCanEditProject(project)

        val bean = BasePropertiesBean(null)
        PluginPropertiesUtil.bindPropertiesFromRequest(request, bean)
        return try {
            val data = handle(project, request, bean.properties)
            writeJson(response, data)
        } catch (controllerException: JsonControllerException) {
            writeError(response, controllerException.message, controllerException.status)
        }
    }

    companion object {
        private const val BUILD_TYPE_ID = "id"
        private const val BUILD_TYPE_PREFIX = "buildType:"
    }
}