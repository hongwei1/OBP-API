package code.api.builder

import java.util.UUID

import code.api.builder.JsonFactory_APIBuilder._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import code.util.Helper.booleanToBox
import net.liftweb.common.{Box, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction._
import net.liftweb.json._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

trait APIMethods_APIBuilder
{
  self: RestHelper =>
  
  val ImplementationsBuilderAPI = new Object()
  {
    val apiVersion: ApiVersion = ApiVersion.apiBuilder
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    implicit val formats = net.liftweb.json.DefaultFormats
    val Account_applicationNotFound = "OBP-31001: Account_application not found. Please specify a valid value for ACCOUNT_APPLICATION_ID."
    def endpointsOfBuilderAPI = getAccount_applications :: getAccount_application :: createAccount_application :: deleteAccount_application :: Nil
    resourceDocs += ResourceDoc(getAccount_applications, apiVersion,
                                "getAccount_applications", "GET",
                                "/account_application",
                                "Get Account_applications",
                                "Return All Account_applications",
                                emptyObjectJson, templatesJson,
                                List(UserNotLoggedIn, UnknownError),
                                Catalogs(notCore, notPSD2, notOBWG),
                                apiTagApiBuilder :: Nil
    )
    lazy val getAccount_applications: OBPEndpoint =
    {
      case ("account_application" :: Nil) JsonGet req =>
        cc =>
        {
          for (u <- cc.user ?~ UserNotLoggedIn; templates <- APIBuilder_Connector.getTemplates; templatesJson = JsonFactory_APIBuilder.createTemplates(
            templates
          ); jsonObject: JValue = decompose(templatesJson)) yield
            {
              successJsonResponse(jsonObject)
            }
        }
    }
    resourceDocs += ResourceDoc(getAccount_application, apiVersion,
                                "getAccount_application", "GET",
                                "/account_application/ACCOUNT_APPLICATION_ID",
                                "Get Account_application",
                                "Return One Account_application By Id",
                                emptyObjectJson, templateJson,
                                List(UserNotLoggedIn, UnknownError),
                                Catalogs(notCore, notPSD2, notOBWG),
                                apiTagApiBuilder :: Nil
    )
    lazy val getAccount_application: OBPEndpoint =
    {
      case ("account_application" :: templateId :: Nil) JsonGet _ =>
        cc =>
        {
          for (
            u <- cc.user ?~ UserNotLoggedIn; 
            template <- APIBuilder_Connector.getTemplateById(templateId) ?~! Account_applicationNotFound; 
            templateJson = JsonFactory_APIBuilder.createTemplate(template); 
            jsonObject: JValue = decompose(templateJson)
          ) yield {
              successJsonResponse(jsonObject)
            }
        }
    }
    resourceDocs += ResourceDoc(createAccount_application, apiVersion,
                                "createAccount_application", "POST",
                                "/account_application",
                                "Create Account_application",
                                "Create One Account_application",
                                createTemplateJson, templateJson,
                                List(UnknownError),
                                Catalogs(notCore, notPSD2, notOBWG),
                                apiTagApiBuilder :: Nil
    )
    lazy val createAccount_application: OBPEndpoint =
    {
      case ("account_application" :: Nil) JsonPost json -> _ =>
        cc =>
        {
          for (
            u <- cc.user ?~ UserNotLoggedIn;
            
            
            createTemplateJson <- tryo(json.extract[CreateTemplateJson]) ?~! InvalidJsonFormat;
            
            _ <- booleanToBox(createTemplateJson.customer_id.startsWith("Dog"), "customer_id must start with Dog");
              

            template <- APIBuilder_Connector.createTemplate(createTemplateJson); 
            templateJson = JsonFactory_APIBuilder.createTemplate(template); 
            jsonObject: JValue = decompose(templateJson)
          ) yield
            {
              successJsonResponse(jsonObject)
            }
        }
    }
    resourceDocs += ResourceDoc(deleteAccount_application, apiVersion,
                                "deleteAccount_application", "DELETE",
                                "/account_application/ACCOUNT_APPLICATION_ID",
                                "Delete Account_application",
                                "Delete One Account_application",
                                emptyObjectJson, emptyObjectJson.copy("true"),
                                List(UserNotLoggedIn, UnknownError),
                                Catalogs(notCore, notPSD2, notOBWG),
                                apiTagApiBuilder :: Nil
    )
    lazy val deleteAccount_application: OBPEndpoint =
    {
      case ("account_application" :: templateId :: Nil) JsonDelete _ =>
        cc =>
        {
          for (u <- cc.user ?~ UserNotLoggedIn; template <- APIBuilder_Connector.getTemplateById(
            templateId
          ) ?~! Account_applicationNotFound; deleted <- APIBuilder_Connector.deleteTemplate(
            templateId
          )) yield
            {
              if (deleted) noContentJsonResponse else errorJsonResponse(
                "Delete not completed"
              )
            }
        }
    }
  }
}

object APIBuilder_Connector
{
  
  val allAPIBuilderModels = List(MappedAccountApplication)
  def createTemplate(createTemplateJson: CreateTemplateJson) = Full(
    MappedAccountApplication
      .create
      .mTemplateId(UUID.randomUUID().toString)
      .mCustomerNumber(createTemplateJson.customer_number)
      .mDate(createTemplateJson.date)
      .mCustomerId(createTemplateJson.customer_id)
      .saveMe()
  )
  def getTemplates() = Full(
    MappedAccountApplication.findAll()
  )
  def getTemplateById(templateId: String) = {
    val abd: Box[MappedAccountApplication] =MappedAccountApplication.find(By(MappedAccountApplication.mTemplateId, templateId))
    val response = HttpClient.makeGetRequest("http://127.0.0.1:8080/obp/v2.1.0/root")
    Full(ApplicationFromHttp(response, "", "", ""))
  }
  
  def deleteTemplate(templateId: String) = MappedAccountApplication.find(
    By(MappedAccountApplication.mTemplateId, templateId)
  ).map(_.delete_!)
}

import net.liftweb.mapper._

class MappedAccountApplication extends AccountApplication with LongKeyedMapper[MappedAccountApplication] with IdPK
{
  def getSingleton = MappedAccountApplication
  
  
  object mCustomerNumber extends MappedString(this, 100)
  override def customer_number: String = mCustomerNumber.get
  
  object mDate extends MappedString(this, 100)
  
  override def date: String = mDate.get
  
  object mTemplateId extends MappedString(this, 100)
  
  override def templateId: String = mTemplateId.get
  
  object mCustomerId extends MappedString(this, 100)
  override def CustomerId: String = mCustomerNumber.get
}

object MappedAccountApplication extends MappedAccountApplication with LongKeyedMetaMapper[MappedAccountApplication]

trait AccountApplication
{
  def customer_number: String
  def date: String
  def templateId: String
  def CustomerId: String
}

case class ApplicationFromHttp(
  customer_number: String,
   date: String,
   templateId: String,
   CustomerId: String
) extends AccountApplication