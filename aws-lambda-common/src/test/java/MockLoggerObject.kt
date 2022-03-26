import jetbrains.buildServer.runner.lambda.directory.Logger
import org.jmock.Expectations
import org.jmock.Mockery

object MockLoggerObject {
    fun Mockery.mockBuildLogger(): Logger {
        val loggerMock = mock(Logger::class.java)

        checking(object : Expectations() {
            init {
                allowing(loggerMock).message(with(any(String::class.java)))
            }
        })

        return loggerMock
    }
}