package code.api.builder
import java.util.UUID

import code.api.builder.JsonFactory_APIBuilder.{createTemplateJson, _}
import code.api.util.APIUtil._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import net.liftweb.common.Full
import net.liftweb.http.S
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json.Extraction._
import net.liftweb.json._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.{tryo, urlDecode}

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
trait APIMethods_APIBuilder { self: RestHelper =>
  val ImplementationsBuilderAPI = new Object() {
    val apiVersion: ApiVersion = ApiVersion.apiBuilder
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    implicit val formats = net.liftweb.json.DefaultFormats
    val TemplateNotFound = "OBP-31001: Template not found. Please specify a valid value for TEMPLATE_ID."
    def endpointsOfBuilderAPI = createCustomerContact :: createDisposer :: Nil

    resourceDocs += ResourceDoc(
      createCustomerContact,
      apiVersion,
      "createCustomerContact",
      "POST",
      "/kundenstamm",
      "Create Customer Contact",
      "CUSTOMER CONTACT CREATION",
      createCustomerContactJson,
      postkundenkontakteResult,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )
    lazy val createCustomerContact: OBPEndpoint = {
      case ("kundenstamm" :: Nil) JsonPost json -> _ =>
        cc => {
          for
            {
            createTemplateJson <- tryo(json.extract[CreateCustomerContact]) ?~! InvalidJsonFormat
            jsonObject = JsonFactory_APIBuilder.createTemplate(createTemplateJson)
          }yield {
            createdJsonResponse(jsonObject)
          }
        }
    }
    
    resourceDocs += ResourceDoc(
      createDisposer,
      apiVersion,
      "createDisposer",
      "POST",
      "/v1/disposers",
      "Create Disposer",
      "Create Disposer",
      createDisposerJson,
      postDisposersResponse,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )
    lazy val createDisposer: OBPEndpoint = {
      case ("v1"::"disposers" :: Nil) JsonPost json -> _ =>
        cc => {
          for
            {
            createTemplateJson <- tryo(json.extract[CreateDisposer]) ?~! InvalidJsonFormat
            jsonObject = JsonFactory_APIBuilder.createDisposer(createTemplateJson)
          }yield {
            createdJsonResponse(jsonObject)
          }
        }
    }
  }
}
object APIBuilder_Connector {
  val allAPIBuilderModels = List(MappedTemplate_4824100653501473508)
//  def createTemplate(createTemplateJson: R00tJsonObject) = Full(MappedTemplate_4824100653501473508.create.mTemplateId(UUID.randomUUID().toString).mAuthor(createTemplateJson.author).mPages(createTemplateJson.pages).mPoints(createTemplateJson.points).saveMe())
//  def getTemplates() = Full(MappedTemplate_4824100653501473508.findAll())
//  def getTemplateById(templateId: String) = MappedTemplate_4824100653501473508.find(By(MappedTemplate_4824100653501473508.mTemplateId, templateId))
//  def deleteTemplate(templateId: String) = MappedTemplate_4824100653501473508.find(By(MappedTemplate_4824100653501473508.mTemplateId, templateId)).map(_.delete_!)
}
import net.liftweb.mapper._
class MappedTemplate_4824100653501473508 extends Template with LongKeyedMapper[MappedTemplate_4824100653501473508] with IdPK {
  object mAuthor extends MappedString(this, 100)
  override def author: String = mAuthor.get
  object mPages extends MappedInt(this)
  override def pages: Int = mPages.get
  object mPoints extends MappedDouble(this)
  override def points: Double = mPoints.get
  def getSingleton = MappedTemplate_4824100653501473508
  object mTemplateId extends MappedString(this, 100)
  override def templateId: String = mTemplateId.get
}
object MappedTemplate_4824100653501473508 extends MappedTemplate_4824100653501473508 with LongKeyedMetaMapper[MappedTemplate_4824100653501473508]
trait Template { `_` =>
  def author: String
  def pages: Int
  def points: Double
  def templateId: String
}