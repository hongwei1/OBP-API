package code.api.v4_0_0.dynamic.practise

// any import statement here need be moved into the process method body

/**
 * practise new endpoint at this object, don't commit you practise code to git
 * 
 * This endpoint is only for testing new dynamic resource/messages method body.
 * eg: when you try the create dynamic resource doc endpoint, you need to prepare the method body properly.
 *   you can prepare the obp scala code just under the method:
 *    `process(callContext: CallContext, request: Req, pathParams: Map[String, String])`,
 *    
 *    
 * 
 */
object PractiseEndpoint extends code.api.v4_0_0.dynamic.DynamicCompileEndpoint {
  // don't modify these import statement
  import code.api.util.CallContext
  import code.api.util.ErrorMessages.{InvalidJsonFormat, InvalidRequestPayload}
  import code.api.util.NewStyle.HttpCode
  import code.api.util.APIUtil.{OBPReturnType, futureToBoxedResponse, scalaFutureToLaFuture, errorJsonResponse}

  import net.liftweb.common.{Box, EmptyBox, Full}
  import net.liftweb.http.{JsonResponse, Req}
  import net.liftweb.json.MappingException

  import scala.concurrent.Future
  import com.openbankproject.commons.ExecutionContext.Implicits.global
  import code.api.v4_0_0.dynamic.DynamicCompileEndpoint._


  // request method
  val requestMethod = "POST"
  val requestUrl = "/my_user/MY_USER_ID"


  // all request case classes
  case class RequestRootJsonClass(name: String, age: Long, hobby: List[String])


  // all response case classes
  case class ResponseRootJsonClass(my_user_id: String, name: String, age: Long, hobby: List[String])

  // * is any bankId, if bound to other bankId, just modify this value to correct one
  override val boundBankId = "*"

  // copy the whole method body as "dynamicResourceDoc" method body
  override protected def
    process(callContext: CallContext, request: Req, pathParams: Map[String, String]) : Box[JsonResponse] = {
    // please add import sentences here, those used by this method
    import code.api.util.NewStyle
    import code.api.v4_0_0.JSONFactory400
    import org.apache.commons.io.FileUtils
    import net.liftweb.common.{Box, Empty, Failure, Full}
    import code.api.v2_0_0.JSONFactory200
    
    val Some(resourceDoc) = callContext.resourceDocument
    val hasRequestBody = request.body.isDefined
    import sys.process._
    import java.net.URL
    import java.io.File

    val src = scala.io.Source.fromURL("http://ir.dcs.gla.ac.uk/resources/linguistic_utils/stop_words")
    val out = new java.io.FileWriter("stop-words-en.txt")
    out.write(src.mkString)
    out.close
    // get Path Parameters, example:
    // if the requestUrl of resourceDoc is /hello/banks/BANK_ID/world
    // the request path is /hello/banks/bank_x/world
    //pathParams.get("BANK_ID") will get Option("bank_x") value
    val myUserId = pathParams("MY_USER_ID")
//    val connectorCodePath = "1232"
//    val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""), connectorCodePath)
//
//    
//    val source2 = getClass().getClassLoader().getResource("custom_webapp") 
//    val source = FileUtils.readFileToString(path, "utf-8")
//    val start = "//---------------- dynamic start -------------------please don't modify this line"
//    val end   = "//---------------- dynamic end ---------------------please don't modify this line"
    
    val requestEntity = request.json match {
      case Full(zson) =>
        try {
            zson.extract[RequestRootJsonClass]
        } catch {
          case e: MappingException =>
            return Full(errorJsonResponse(s"$InvalidJsonFormat ${e.msg}"))
        }
      case _: EmptyBox =>
        return Full(errorJsonResponse(s"$InvalidRequestPayload Current request has no payload"))
    }
    // please add business logic here
    val responseBody:ResponseRootJsonClass = ResponseRootJsonClass(s"${myUserId}_from_path", requestEntity.name, requestEntity.age,  requestEntity.hobby)

    for {
      (banks, callContext) <- NewStyle.function.getBanks(Some(callContext))
    } yield {
      (JSONFactory400.createBanksJson(banks), HttpCode.`200`(callContext))
//      ("123456", HttpCode.`200`(callContext))
    }
  }

}
