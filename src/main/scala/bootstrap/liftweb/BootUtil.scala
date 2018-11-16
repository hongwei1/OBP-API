package bootstrap.liftweb

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import net.liftweb.util.Props._

import scala.util.control.NoStackTrace

object BootUtil extends MdcLoggable{
  def getPropsFromOSEnvironment: Unit ={
    logger.debug("Enter getPropsFromEnvironmentXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
    var dbUrl:String = 
      try {
        sys.env("HOME")
//      sys.env("EBKNG_POSTGRESQL_DATASOURCE_URL") + "?user=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_USERNAME") +
//        "&password=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_PASSWORD") + "&currentSchema=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_USERNAME")
    } catch {
      case e: Throwable => 
          throw new Exception("dbUrl not completely set (must be url, username and password") with NoStackTrace
          
    }
    logger.debug("The new db.url is: " + dbUrl)
     val fred = net.liftweb.util.Props.prependProvider(Map("db.driver" -> "org.postgresql.Driver"))
     prependProvider(Map("db.url" -> dbUrl))
  }
}
   