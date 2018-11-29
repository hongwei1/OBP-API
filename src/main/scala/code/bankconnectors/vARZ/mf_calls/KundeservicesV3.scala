package code.bankconnectors.vARZ.mf_calls

import code.api.util.APIUtil
import code.bankconnectors.vARZ.HttpClient._
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser._
import net.liftweb.json.Extraction.decompose


object KundeservicesV3  {

    val baseUrl  = APIUtil.getPropsValue("base.url.kunde").getOrElse("") 
    implicit val formats = net.liftweb.json.DefaultFormats

    def postPrivatenkundenkontakte(request: PostPrivatkundenkontakteRequest): PostkundenkontakteResult  = {

      val path = baseUrl + APIUtil.getPropsValue("backendCalls.postPrivatenkundenkontakte").getOrElse("")

      val json: JValue = decompose(request)

      val result = makePostRequest(json, path)
      try {
        parse(result).extract[PostkundenkontakteResult]
      } catch {
        case e: net.liftweb.json.MappingException => throw new Exception
      }
    }
  
  // Liefert einen Kunde
  def getKunde(kundennummer: String): GetKundenResponseJson = {
    val path = s"$baseUrl/__arz_service/kundeservices/api/v3/kunden/$kundennummer" 
    val result = makeGetRequest(path)
    try {
      parse(result).extract[GetKundenResponseJson]
    } catch {
      case e: net.liftweb.json.MappingException => throw new Exception("OBP-50201: Connector did not return the set of accounts we requested")
    }
  }


}
