package bootstrap.liftweb

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import net.liftweb.util.Props._

import scala.util.control.NoStackTrace

object BootUtil extends MdcLoggable{
  def getPropsFromOSEnvironment: Unit ={
    var dbUrl:String = 
      try {
      sys.env("EBKNG_POSTGRESQL_DATASOURCE_URL") + "?user=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_USERNAME") +
        "&password=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_PASSWORD") + "&schema=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_USERNAME") + 
        "&ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory"
    } catch {
      case e: Throwable => 
          throw new Exception("dbUrl not completely set (must be url, username and password") with NoStackTrace
          
    }
    logger.debug("The new db.url is: " + dbUrl)
     prependProvider(Map("db.driver" -> "org.postgresql.Driver"))
     prependProvider(Map("db.url" -> dbUrl))
  }
}
   