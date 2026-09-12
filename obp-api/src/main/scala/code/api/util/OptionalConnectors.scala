package code.api.util

/**
 * Connector implementations that a deployment may or may not have.
 *
 * `Connector.nameToConnector` is a fixed map naming every implementation, which means the core
 * names each one at compile time. That is right for the connectors OBP ships — mapped, rabbitmq,
 * grpc and the rest are all part of obp-api — but not for `internal`, which executes Scala the
 * operator uploaded and therefore lives with the runtime compiler in an optional module.
 *
 * A connector registered here joins the map. With nothing registered, `connector=internal` fails
 * the way any unknown connector name already does (InvalidConnector), which is the truthful
 * outcome: on an instance without the compiler, that connector genuinely does not exist.
 */
object OptionalConnectors {

  @volatile private var registered: Map[String, code.bankconnectors.Connector] = Map.empty

  /** Called during boot by whichever module supplies the implementation. */
  def register(name: String, connector: code.bankconnectors.Connector): Unit =
    registered = registered + (name -> connector)

  /** Test hook. */
  def clear(): Unit = registered = Map.empty

  def all: Map[String, code.bankconnectors.Connector] = registered
}
