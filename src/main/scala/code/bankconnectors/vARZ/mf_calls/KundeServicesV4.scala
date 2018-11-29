package code.bankconnectors.vARZ.mf_calls

import code.api.util.APIUtil
import net.liftweb.json.JsonParser.parse
import code.bankconnectors.vARZ.HttpClient.makeGetRequest
import scala.util.control.NoStackTrace

object KundeServicesV4 {
  
  implicit val formats = net.liftweb.json.DefaultFormats
  val baseUrl  = APIUtil.getPropsValue("base.url.kundeservicesv4").getOrElse("")  
  
  
  def getKonten(kundennummer: String): List[Konto] = {
    val path = baseUrl + "/kunden/" + kundennummer + "/konten"
    val result = makeGetRequest(path)
    try {
      parse(result).extract[List[Konto]]
    } catch {
      case e: net.liftweb.json.MappingException => throw new Exception("OBP-50201: Connector did not return the set of accounts we requested") with NoStackTrace
    }
  }
}
