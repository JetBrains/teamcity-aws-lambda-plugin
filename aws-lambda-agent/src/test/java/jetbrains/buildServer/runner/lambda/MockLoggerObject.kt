package jetbrains.buildServer.runner.lambda

import jetbrains.buildServer.agent.BuildProgressLogger
import org.jmock.Expectations
import org.jmock.Mockery

object MockLoggerObject {
    fun Mockery.mockBuildLogger(): BuildProgressLogger {
        val loggerMock = mock(BuildProgressLogger::class.java)

        checking(object : Expectations() {
            init {
                allowing(loggerMock).message(with(any(String::class.java)))
            }
        })

        return loggerMock
    }
}