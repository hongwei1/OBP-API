package code.api.builder
import code.api.util.APIUtil
case class CreateTemplateJson(customer_number: String = """1234""", customer_id: String = """1234-id""",date: String = """19880313""")
case class TemplateJson(id: String = """11231231312""", customer_number: String = """1234""", date: String = """19880313""")
object JsonFactory_APIBuilder {
  val templateJson = TemplateJson()
  val templatesJson = List(templateJson)
  val createTemplateJson = CreateTemplateJson()
  def createTemplate(template: AccountApplication) = 
    {
    TemplateJson(template.templateId, template.customer_number, template.date)
    }
  def createTemplates(templates: List[AccountApplication]) = templates.map(template => TemplateJson(template.templateId, template.customer_number, template.date))
  val allFields = for (v <- this.getClass.getDeclaredFields; if APIUtil.notExstingBaseClass(v.getName())) yield {
    v.setAccessible(true)
    v.get(this)
  }
}


import code.util.Helper.MdcLoggable
import net.liftweb.json.JsonAST.{JValue, compactRender}
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
object HttpClient extends MdcLoggable {
  val clientToCbs = HttpClients.createDefault()
  def makePostRequest(json: JValue, path: String): String = {
    val post = new HttpPost(path)
    val jsonBody = new StringEntity(compactRender(json), "UTF-8")
    post.setEntity(jsonBody)
    logger.debug(s"$path--Request : "+post.toString +"\n Body is :" + compactRender(json) +
      "/n RealBody is: " + jsonBody.getContent().toString)
    val response = clientToCbs.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug(s"$path--Response : "+response.toString+ "\n Body is :"+result)
    if (result.startsWith("<")) throw new Exception(s"InvalidRequestFormat, current Request is $result") else result
  }
  def makeGetRequest(path: String): String = {
    val httpGet = new HttpGet(path)
    httpGet.addHeader("Content-Type", "application/json;charset=utf-8")
    logger.debug(s"$path--Request : " + httpGet.toString)
    val response = clientToCbs.execute(httpGet)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug(s"$path--Response : " + response.toString + "\n Body is :" + result)
    result
  }
}

object add extends App{
  println(HttpClient.makeGetRequest("http://localhost:8082/?version=b1&list-all-banks=false#vb1-getAccount_application"))
//  println(HttpClient.makeGetRequest("https://apisandbox.openbankproject.com/obp/v3.0.0"))
}