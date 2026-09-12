package code.api.util

/**
 * The request/response shapes of the built-in practise dynamic endpoint.
 *
 * They live here rather than on `PractiseEndpoint` because the core's swagger definitions use
 * `RequestRootJsonClass` as a worked example, and `PractiseEndpoint` extends
 * `DynamicCompileEndpoint` — i.e. it belongs with the runtime compiler. Having the swagger
 * document depend on the compiler would put the toolbox on the required path of every deployment,
 * including the ones that ship without it.
 *
 * The simple names are unchanged on purpose: the generated swagger definition is named after
 * them, so renaming would alter the published document.
 */
object PractiseEndpointJson {
  case class RequestRootJsonClass(name: String, age: Long, hobby: List[String])
  case class ResponseRootJsonClass(my_user_id: String, name: String, age: Long, hobby: List[String])
}
