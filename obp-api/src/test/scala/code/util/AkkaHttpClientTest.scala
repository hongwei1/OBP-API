package code.util

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.HttpResponse
import akka.util.ByteString
import code.util.Helper.MdcLoggable
import net.liftweb
import net.liftweb.json.Extraction
import org.scalatest.{FeatureSpec, Matchers}
import code.util.AkkaHttpClient._

import scala.concurrent.duration._
import scala.concurrent.Await

class AkkaHttpClientTest extends FeatureSpec with Matchers with MdcLoggable {

  val TIMEOUT = (10 seconds)
  
  feature("test the akka http client") 
  {
    scenario("Just Run it, first make it work"){
      val uri = "https://www.openbankproject.com"
      makeHttpRequest(prepareHttpRequest(uri,GET))
  
     
      case class User(name: String, job: String)
      val user = User("morpheus", "leader")
      val json = liftweb.json.compactRender(Extraction.decompose(user))
      makeHttpRequest(prepareHttpRequest("https://reqres.in/api/users",POST,entityJsonString=json)) map {
        `POST response` =>
          org.scalameta.logger.elem(`POST response`.status)
          `POST response`.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
              val `Got POST response, body: ` = body.utf8String
              println(`Got POST response, body: `)
          }
      }    
      
      val user2 = User("morpheus", "zion resident")
      val json2 = liftweb.json.compactRender(Extraction.decompose(user2))
      makeHttpRequest(prepareHttpRequest("https://reqres.in/api/users",PUT,entityJsonString=json2)) map {
        `PUT response` =>
          org.scalameta.logger.elem(`PUT response`.status)
          `PUT response`.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
              val `Got PUT response, body: ` = body.utf8String
              println(`Got PUT response, body: `)
          }
      }
    }
  }
  
}