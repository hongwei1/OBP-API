package bootstrap.liftweb

import code.util.Helper.MdcLoggable
import net.liftweb.util.Props.appendProvider

object BootUtil {
  def getPropsFromOSEnvironment: Unit ={
    var dbUrl:String = 
      try {
      sys.env("EBKNG_POSTGRESQL_DATASOURCE_URL" + "?user=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_USERNAME" +
        "&password=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_PASSWORD") + "&currentSchema=" + sys.env("EBKNG_POSTGRESQL_DATASOURCE_USERNAME")))
    } catch {
      case e: Throwable => 
          throw new Exception("dbUrl not completely set (must be url, username and password")
          
    }
     appendProvider(Map("db.url" -> dbUrl))
  }
}
   