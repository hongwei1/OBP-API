package code.bankconnectors.vARZ

import java.util.UUID

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import net.liftweb.json.JsonAST.{JValue, compactRender}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients

object HttpClient extends MdcLoggable {

  val clientToCbs = HttpClients.createDefault()

  def makePostRequest(json: JValue, path: String): String = {
    val post = new HttpPost(path)
    post.addHeader("Content-Type", "application/json;charset=utf-8")
    post.addHeader("Authorization", APIUtil.getPropsValue("arz.authorization","arz.authorization"))
    post.addHeader("arz-request-thread", UUID.randomUUID().toString)
    post.addHeader("arz-tenant", "499")
    post.addHeader("arz-variante", "0")
    post.addHeader("arz-accounting-id", "TESOBE")
    post.addHeader("arz-request-origin", "1")
    post.addHeader("idempotency-key", UUID.randomUUID().toString)
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

}