package bootstrap.liftweb

import code.api.util.APIUtil
import net.liftweb.common.{Box, Full}
import net.liftweb.db.SuperConnection
import net.liftweb.mapper.{ConnectionIdentifier, StandardDBVendor}

/**
  * A specialised StandardDBVendor that vends JDBC connections within the Mapper ORM framework that support 
  * explicit specification of a schema (e.g., for PostgreSQL schema support). 
  */
class SchemaAwareDBVendor(
  driverName: String,
  dbUrl: String,
  dbUser: Box[String],
  dbPassword: Box[String],
  schema: String = APIUtil.getSchema
) extends StandardDBVendor(driverName, dbUrl, dbUser, dbPassword)
{
  /** Override the superconnection factory to return a boxed 
    * SuperConnection that has an explicit schema name set. */
  override def newSuperConnection(name: ConnectionIdentifier): Box[SuperConnection] =
  {
    val connection = newConnection(name).openOrThrowException("Looking for Connection Identifier " + name + " but failed to find either a JNDI data source " +
                                   "with the name " + name.jndiName + " or a lift connection manager with the correct name")
    
    def release = () => releaseConnection(connection)
    
    Full(new SuperConnection(connection, release, Full(schema)))
  }
}