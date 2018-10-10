package code.bankconnectors.vARZ.mf_calls

import code.api.util.APIUtil
import code.bankconnectors.vARZ.HttpClient.makePostRequest
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser._
import net.liftweb.json.Extraction.decompose

object PostDisposers  {
  def postDisposers(request: PostDisposersRequest): PostDisposersResponse  = {

    implicit val formats = net.liftweb.json.DefaultFormats

    
    val path = APIUtil.getPropsValue("base.url.disposer").getOrElse("") +
      APIUtil.getPropsValue("backendCalls.diposers").getOrElse("")



    val json: JValue = decompose(request)

    val result = makePostRequest(json, path)
    try {
      parse(result).extract[PostDisposersResponse]
    } catch {
      case e: net.liftweb.json.MappingException => throw new Exception
    }
  }
  
}
