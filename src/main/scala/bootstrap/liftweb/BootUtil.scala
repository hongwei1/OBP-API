package bootstrap.liftweb

import code.util.Helper.MdcLoggable
import net.liftweb.util.Props
import net.liftweb.util.Props._

import scala.util.control.NoStackTrace

object BootUtil extends MdcLoggable{
  def getPropsFromOSEnvironment: Unit ={
    try {
      val dbUrl = sys.env("EBKNG_POSTGRESQL_DATASOURCE_URL") + "?user=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_USERNAME") +
        "&password=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_PASSWORD") + "&schema=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_USERNAME") + 
        "&ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory"
      logger.debug("The new db.url is: " + dbUrl)
      prependProvider(Map("db.driver" -> "org.postgresql.Driver"))
      prependProvider(Map("db.url" -> dbUrl))
    } catch {
      case e: Throwable => 
        //NOTE: These sys.evn only work for Production mode, other mode, just ignore these setting.
        if (Props.mode == Props.RunModes.Production) {
          logger.error("dbUrl not completely set (must be url, username and password")
          throw new Exception("dbUrl not completely set (must be url, username and password") with NoStackTrace
        }
        else
          ""
    }
  }
}
   