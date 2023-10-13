package bootstrap.liftweb

import com.zaxxer.hikari.pool.ProxyConnection
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import java.sql.Connection
import net.liftweb.common.{Box, Full, Logger}
import net.liftweb.db.ConnectionManager
import net.liftweb.util.{ConnectionIdentifier, Props, StringHelpers}
import net.liftweb.util.Helpers.tryo

import java.util.Properties

/**
 * The Custom DB vendor.
 *
 * @param driverName the name of the database driver
 * @param dbUrl the URL for the JDBC data connection
 * @param dbUser the optional username
 * @param dbPassword the optional db password
 */
class CustomDBVendor(driverName: String,
                     dbUrl: String,
                     dbUser: Box[String],
                     dbPassword: Box[String]) extends CustomProtoDBVendor {

  private val logger = Logger(classOf[CustomDBVendor])

  object HikariDatasource {
    
    val properties = new Properties();

//    val b = a.filter(_._1.startsWith("hikari"))
//    b.map(props => properties.setProperty(props._1, props._2))
    
    //1st: get all the fields from Hikari class, here we can remove the dataSourceClassName,jdbcUrl,username,password
//    eg:keepaliveTime,connectionTimeout... 
    
    //minimumIdle, autoCommit are not the same as fields.
//    maximumPoolSize
//    isIsolateInternalQueries
//    isAllowPoolSuspension --> allowPoolSuspension
//    isReadOnly --> allowPoolSuspension
//    isRegisterMbeans --> registerMbeans
//    transactionIsolation --> 
    
    
    //2rd: try to get all the values from OBP.getProps with prefix: hakari.xxx
//    eg: hakari.keepaliveTime, hakari.connectionTimeout
    
    
    //3rd: then we can insert this values into properties.
//    (keepaliveTime,3000),(connectionTimeout,10000),,,,
    
    //4rd: then we can set the properties to new HikariConfig(properties)
    
    properties.setProperty("autoCommit","false")
    properties.setProperty("connectionTimeout","1000")
    properties.setProperty("keepaliveTime","1000")
    properties.setProperty("maxLifetime","1000")
    properties.setProperty("connectionTestQuery","1000")
    
    val config = new HikariConfig(properties)

    config.getDataSourceProperties
    
    (dbUser, dbPassword) match {
      case (Full(user), Full(pwd)) =>
        config.setJdbcUrl(dbUrl)
        config.setUsername(user)
        config.setPassword(pwd)
      case _ =>
        config.setJdbcUrl(dbUrl)
    }
    
    config.addDataSourceProperty("cachePrepStmts", "true")
    config.addDataSourceProperty("prepStmtCacheSize", "250")
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

    val ds: HikariDataSource = new HikariDataSource(config)
  }

  def createOne: Box[Connection] =  {
    tryo{t:Throwable => logger.error("Cannot load database driver: %s".format(driverName), t)}{Class.forName(driverName);()}
    tryo{t:Throwable => logger.error("Unable to get database connection. url=%s".format(dbUrl),t)}(HikariDatasource.ds.getConnection())
  }

  def closeAllConnections_!(): Unit = HikariDatasource.ds.close()
}

trait CustomProtoDBVendor extends ConnectionManager {
  private val logger = Logger(classOf[CustomProtoDBVendor])

  def createOne: Box[Connection]

  def newConnection(name: ConnectionIdentifier): Box[Connection] = {
    createOne
  }

  def releaseConnection(conn: Connection): Unit = {conn.asInstanceOf[ProxyConnection].close()}

}

object myApp extends App{
  import scala.jdk.CollectionConverters.mapAsScalaMapConverter
  // OBP props eg: hikari.autoCommit= true
  val oboProps = Props.props.toList
  val obpHikariProps: List[(String, String)] = oboProps.filter(_._1.startsWith("hikari"))


  //system props example: 
  //  eg: OBP_HIKARI_AUTO_COMMIT=true
  val systemProps = System.getenv().asScala.toList
  val systemHikariProps: List[(String, String)] = oboProps.filter(_._1.contains("HIKARI"))
  //we need to convert system props format to OBP format: OBP_AUTO_COMMIT=> hikari.autoCommit
  val systemHikariPropsConverted = systemHikariProps.map(systemProps => 
    (
      "hikari"+StringHelpers.camelifyMethod(systemProps._1.split("HIKARI").apply(1)), 
      systemProps._2
    )
  )
  val a120 = "hikari."+StringHelpers.camelifyMethod("OBP_HIKARI_AUTO_COMMIT".split("HIKARI_").apply(1))
  
  val properties = new Properties();
//  b.map(props => properties.setProperty(props._1, props._2))
//  println(b)
}