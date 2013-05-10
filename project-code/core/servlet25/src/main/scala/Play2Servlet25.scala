package play.core.server.servlet25

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.api.Logger
import play.core.server.servlet.GenericPlay2Servlet
import play.core.server.servlet.RichHttpServletRequest
import play.core.server.servlet.RichHttpServletResponse
import java.util.concurrent.atomic.AtomicBoolean

object Play2Servlet {

  val DEFAULT_TIMEOUT = 10 * 1000

  val syncTimeout = GenericPlay2Servlet.configuration.getInt("servlet25.synctimeout").getOrElse(DEFAULT_TIMEOUT)
  Logger("play").debug("Sync timeout for HTTP requests: " + syncTimeout + " seconds")
}

class Play2Servlet extends GenericPlay2Servlet[Tuple3[HttpServletRequest, HttpServletResponse, Object]] with Helpers {

  protected override def onBeginService(request: HttpServletRequest, response: HttpServletResponse): Tuple3[HttpServletRequest, HttpServletResponse, Object] = {
    (request, response, new AtomicBoolean(false))
  }

  protected override def onFinishService(execContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]) = {
    execContext._3.synchronized {
      if(!execContext._3.asInstanceOf[AtomicBoolean].get){
        val start = System.currentTimeMillis
        execContext._3.wait(Play2Servlet.syncTimeout)
        val end = System.currentTimeMillis
        if(Play2Servlet.syncTimeout > 0 && end - start >= Play2Servlet.syncTimeout){
          val req = execContext._1
          println("***********************************************************************")
          println("[Timeout]" + new java.util.Date().toString())
          println("method: " + req.getMethod)
          println("requestURI: " + req.getRequestURI)
          println("requestURL: " + req.getRequestURL.toString)
          println("querySring: " + req.getQueryString)
          println("headers:")
          val e = req.getHeaderNames()
          while(e.hasMoreElements){
            val name  = e.nextElement
            val value = req.getHeader(name)
            println("  %s: %s".format(name, value))
          }
          println("***********************************************************************")
        }
      }
    }
  }

  protected override def onHttpResponseComplete(execContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]) = {
    execContext._3.synchronized {
      execContext._3.asInstanceOf[AtomicBoolean].set(true)
      execContext._3.notify()
    }
  }

  protected override def getHttpRequest(executionContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]): RichHttpServletRequest = {
    new RichHttpServletRequest {
      def getRichInputStream(): Option[java.io.InputStream] = {
        Option(executionContext._1.getInputStream)
      }
    }
  }

  protected override def getHttpResponse(executionContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]): RichHttpServletResponse = {
    new RichHttpServletResponse {
      def getRichOutputStream: Option[java.io.OutputStream] = {
        Option(executionContext._2.getOutputStream)
      }
  
      def getHttpServletResponse: Option[HttpServletResponse] = {
        Option(executionContext._2)
      }
    }
  }

}