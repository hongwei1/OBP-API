package code.api.builder
import code.api.util.APIUtil
import code.bankconnectors.vARZ.mf_calls.{Error, PostDisposersResponse, PostkundenkontakteResult, Problem}
import net.liftweb.json
case class CreateTemplateJson(author: String = """Chinua Achebe""", pages: Int = 209, points: Double = 1.3)

case class Kundenstamm(
  famname: String ="",
  vorname: String ="",
  mobiltel: String ="",
  emailadr: String =""
)
case class CreateCustomerContact(
  kundenstamm: Kundenstamm = Kundenstamm(),
  uuid: String  =""
)

case class Credentials(
  name: String="",
  pin: String=""
)
case class Address(
  identifier: String ="",
  number: Int =5
)
case class CreateDisposer(
  credentials: Credentials = Credentials(),
  status: String ="",
  language: String="",
  `type`: String="",
  customerNr: Int = 1,
  address: Address = Address(),
  bankSupervisorId: String=""
)


case class TemplateJson(template_id: String = """11231231312""", author: String = """Chinua Achebe""", pages: Int = 209, points: Double = 1.3)
object JsonFactory_APIBuilder {
  val templateJson = TemplateJson()
  val templatesJson = List(templateJson)
  val createTemplateJson = CreateTemplateJson()
  val createCustomerContactJson = CreateCustomerContact()
  val postkundenkontakteResult = PostkundenkontakteResult(
    1,
    List("String"),
    Problem( title ="",
    detail = "",
    `type`= "",
    status = 1,
    errId = "",
    errors= List(Error("","","")
  )))
  val postDisposersResponse = PostDisposersResponse(12)
  
  val createDisposerJson = CreateDisposer()
  def createTemplate(template: CreateCustomerContact) = {
     val jsonStringFromFile = scala.io.Source.fromFile("src/main/scala/code/bankconnectors/vARZ/Doc/createCustomerContactCreationOutput.json").mkString 
     json.parse(jsonStringFromFile)
    
  }
  def createDisposer(template: CreateDisposer) = {
     val jsonStringFromFile = scala.io.Source.fromFile("src/main/scala/code/bankconnectors/vARZ/Doc/createDisposerOutput.json").mkString 
     json.parse(jsonStringFromFile)
    
  }
  def createTemplates(templates: List[Template]) = templates.map(template => TemplateJson(template.templateId, template.author, template.pages, template.points))
  val allFields = for (v <- this.getClass.getDeclaredFields; if APIUtil.notExstingBaseClass(v.getName())) yield {
    v.setAccessible(true)
    v.get(this)
  }
}