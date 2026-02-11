package code.bankconnectors.generator

import code.api.util.CodeGenerateUtils.createDocExample
import code.api.util.{APIUtil, CallContext}
import code.bankconnectors.Connector
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ReflectUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils.uncapitalize

import java.io.File
import java.net.URL
import java.util.Date
import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

/**
 * this is util for Connector builders, this should never be called by product code.
 */
object ConnectorBuilderUtil extends MdcLoggable {
  
  def getClassesFromPackage(packageName: String): List[Class[_]] = {
    val classLoader = Thread.currentThread().getContextClassLoader
    val path = packageName.replace('.', '/')
    val resources: Seq[URL] = classLoader.getResources(path).asScala.toSeq

    resources.flatMap { resource =>
      val file = new File(resource.toURI)
      if (file.isDirectory) {
        file.listFiles()
          .filter(_.getName.endsWith(".class"))
          .map(_.getName.stripSuffix(".class"))
          .map(className => Class.forName(s"$packageName.$className"))
      } else {
        Seq.empty
      }
    }.toList
  }
  
  
  // rewrite method code.webuiprops.MappedWebUiPropsProvider#getWebUiPropsValue, avoid access DB cause dataSource not found exception
  {
    import javassist.ClassPool
    val pool = ClassPool.getDefault
    //NOTE: MEMORY_USER this ctClass will be cached in ClassPool, it may load too many classes into heap. 
    val ctClass = pool.getCtClass("code.webuiprops.MappedWebUiPropsProvider$")
    val m = ctClass.getDeclaredMethod("getWebUiPropsValue")
    m.insertBefore("""return ""; """)
    ctClass.toClass
    // if(ctClass != null) ctClass.detach()
  }

  /**
   * //def getAdapterInfo(callContext: Option[CallContext]) : Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = ??? 
   * //def validateAndCheckIbanNumber(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[IbanChecker]] = ??? 
   *
   *  This method will only extract the first return class from the method, this is the OBP pattern. 
   *  so we can use it for generate the commons case class.
   *
   *  eg: getAdapterInfo -->  return InboundAdapterInfoInternal
   *  validateAndCheckIbanNumber -->return IbanChecker
   */
  def extractReturnModel(tp: ru.Type): ru.Type = {
    if (tp.typeArgs.isEmpty) {
      tp
    } else {
      extractReturnModel(tp.typeArgs(0))
    }
  }

  val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader)
  val clazz: ru.ClassSymbol = mirror.typeOf[Connector].typeSymbol.asClass
  val connectorDecls: MemberScope = mirror.typeOf[Connector].decls
  val connectorDeclsMethods: Iterable[Symbol] = connectorDecls.filter(symbol => {
    val isMethod = symbol.isMethod && !symbol.asMethod.isVal && !symbol.asMethod.isVar && !symbol.asMethod.isConstructor && !symbol.isProtected
    isMethod})
  val connectorDeclsMethodsReturnOBPRequiredType: Iterable[MethodSymbol] = connectorDeclsMethods
    .map(it => it.asMethod)
    .filter(it => {
      extractReturnModel(it.returnType).typeSymbol.fullName.matches("((code\\.|com.openbankproject\\.).+)|(scala\\.Boolean)") //to make sure, it returned the OBP class and Boolean.
    })
  
  private val classMirror: ru.ClassMirror = mirror.reflectClass(clazz)
  
  /*
    * generateMethods and buildMethods has the same function, only responseExpression parameter type
    * different, because overload method can't compile for different responseExpression parameter.
   */

  def generateMethods(connectorMethodNames: List[String], connectorCodePath: String, responseExpression: String,
                      setTopic: Boolean = false, doCache: Boolean = false) =
    buildMethods(connectorMethodNames, connectorCodePath, _ => responseExpression, setTopic, doCache)

  def buildMethods(connectorMethodNames: List[String], connectorCodePath: String, connectorMethodToResponse: String => String,
                   setTopic: Boolean = false, doCache: Boolean = false): Unit = {

     val nameSignature: Iterable[ConnectorMethodGenerator] = ru.typeOf[Connector].decls
      .filter(_.isMethod)
      .filter(it => connectorMethodNames.contains(it.name.toString))
      .map(it => {
        val (methodName, typeSignature) = (it.name.toString, it.typeSignature)
        ConnectorMethodGenerator(methodName, typeSignature)
      })

    // check whether some methods names are wrong typo
    if(connectorMethodNames.size > nameSignature.size) {
      val generatedMethodsNames = nameSignature.map(_.methodName).toSet
      val invalidMethodNames = connectorMethodNames.filterNot(generatedMethodsNames.contains(_))
      throw new IllegalArgumentException(s"Some methods not be supported, please check following methods: ${invalidMethodNames.mkString(", \n")}")
    }

    val codeList = nameSignature.map(_.toCode(connectorMethodToResponse, setTopic, doCache))

    //  private val types: Iterable[ru.Type] = symbols.map(_.typeSignature)
    //  println(symbols)
    println("-------------------")
    codeList.foreach(println(_))
    println("===================")

    val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""), connectorCodePath)
    val source = FileUtils.readFileToString(path, "utf-8")
    val start = "//---------------- dynamic start -------------------please don't modify this line"
    val end   = "//---------------- dynamic end ---------------------please don't modify this line"
    val placeHolderInSource = s"""(?s)$start.+$end"""
    val currentTime = APIUtil.DateWithSecondsFormat.format(new Date())
    val insertCode =
      s"""$start
         |// ---------- created on $currentTime
         |${codeList.mkString}
         |// ---------- created on $currentTime
         |$end """.stripMargin
    val newSource = source.replaceFirst(placeHolderInSource, insertCode)
    FileUtils.writeStringToFile(path, newSource, "utf-8")
  }


  private case class ConnectorMethodGenerator(methodName: String, tp: Type) {
    private[this] def paramAnResult = tp.toString
      .replaceAll("""[.\w]+\.(\w+\.([A-Z]+\b|Value)\b)""", "$1") // two times replaceAll to delete package name, but keep enum type name
      .replaceAll("""([.\w]+\.){2,}(\w+\b)""", "$2")
      .replaceFirst("\\)", "): ")
      .replace("cardAttributeType: Value", "cardAttributeType: CardAttributeType.Value") // scala enum is bad for Reflection
      .replace("productAttributeType: Value", "productAttributeType: ProductAttributeType.Value") // scala enum is bad for Reflection
      .replace("accountAttributeType: Value", "accountAttributeType: AccountAttributeType.Value") // scala enum is bad for Reflection
      .replaceFirst("""\btype\b""", "`type`")

    private[this] val params = tp.paramLists(0).filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]).map(_.name.toString).mkString(", ", ", ", "").replaceFirst("""\btype\b""", "`type`")
    private[this] val description = methodName.replaceAll("""(\w)([A-Z])""", "$1 $2").capitalize

    private[this] val entityName = methodName.replaceFirst("^[a-z]+(OrUpdate)?", "")

    private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

    private[this] val isOBPReturnType = resultType.startsWith("OBPReturnType[")

    private[this] val outBoundExample = {
      var typeName = s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}"
      val outBoundType = ReflectUtils.getTypeByName(typeName)
      createDocExample(outBoundType).replaceAll("(?m)^(\\S)", "      $1")
    }
    private[this] val inBoundExample = {
      var typeName = s"com.openbankproject.commons.dto.InBound${methodName.capitalize}"
      val inBoundType = ReflectUtils.getTypeByName(typeName)
      createDocExample(inBoundType).replaceAll("(?m)^(\\S)", "      $1")
    }

    var signature = s"$methodName$paramAnResult"

    val hasCallContext = tp.paramLists(0)
      .exists(_.asTerm.info =:= ru.typeOf[Option[CallContext]])

    /**
     * Get all the parameters name as a String from `typeSignature` object.
     * eg: it will return
     * , bankId, accountId, accountType, accountLabel, currency, initialBalance, accountHolderName, branchId, accountRoutingScheme, accountRoutingAddress
     */
    private[this] val parametersNamesString = tp.paramLists(0)//paramLists will return all the curry parameters set.
      .filterNot(_.asTerm.info =:= ru.typeOf[Option[CallContext]]) // remove the `CallContext` field.
      .map(_.name.toString)//get all parameters name
      .map(it => if(it =="type") "`type`" else it)//This is special case for `type`, it is the keyword in scala.
      .map(it => if(it == "queryParams") "OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams)" else it)
    match {
      case Nil if hasCallContext => "callContext.map(_.toOutboundAdapterCallContext).orNull"
      case Nil => ""
      case list:List[String] if hasCallContext => list.mkString("callContext.map(_.toOutboundAdapterCallContext).orNull, ", ", ", "")
      case list:List[String] => list.mkString(", ")
    }

    // for cache
    private[this] val cacheMethodName = if(resultType.startsWith("Box[")) "memoizeSyncWithProvider" else "memoizeWithProvider"

    private[this] val timeoutFieldName = uncapitalize(methodName.replaceFirst("^[a-z]+", "")) + "TTL"
    private[this] val cacheTimeout = ReflectUtils.findMethod(ru.typeOf[code.bankconnectors.rabbitmq.RabbitMQConnector_vOct2024], timeoutFieldName)(_ => true)
      .map(_.name.toString)
      .getOrElse("accountTTL")

    // end for cache

    private val outBoundName = s"OutBound${methodName.capitalize}"
    private val inBoundName = s"InBound${methodName.capitalize}"

    val inboundDataFieldType = ReflectUtils.getTypeByName(s"com.openbankproject.commons.dto.$inBoundName")
      .member(TermName("data")).asMethod
      .returnType.toString.replaceAll(
      """(\w+\.)+(\w+\.Value)|(\w+\.)+(\w+)""", "$2$4"
    )

    def toCode(responseExpression: String => String, setTopic: Boolean = false, doCache: Boolean = false) = {
      val (outBoundTopic, inBoundTopic) =  setTopic match {
        case true =>
          (s"""Some(Topics.createTopicByClassName("$outBoundName").request)""" ,
           s"""Some(Topics.createTopicByClassName("$outBoundName").request)""" )
        case false => (None, None)
      }

      val callContext = if(hasCallContext) {
        ""
      } else {
        "\n        val callContext: Option[CallContext] = None"
      }

      var body =
      s"""|    import com.openbankproject.commons.dto.{$inBoundName => InBound, $outBoundName => OutBound}  $callContext
          |        val req = OutBound($parametersNamesString)
          |        val response: Future[Box[InBound]] = ${responseExpression(methodName)}
          |        response.map(convertToTuple[$inboundDataFieldType](callContext))        """.stripMargin


      if(doCache && methodName.matches("^(get|check|validate).+")) {
        signature = signature.replaceFirst("""(\b\S+)\s*:\s*Option\[CallContext\]""", "@CacheKeyOmit callContext: Option[CallContext]")
        body =
          s"""    /**
             |      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
             |      * is just a temporary value field with UUID values in order to prevent any ambiguity.
             |      * The real value will be assigned by Macro during compile time at this line of a code:
             |      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
             |      */
             |    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
             |    CacheKeyFromArguments.buildCacheKey {
             |      Caching.${cacheMethodName}(Some(cacheKey.toString()))($cacheTimeout seconds) {
             |
             |    ${body.replaceAll("(?m)^ ", "     ")}
             |
             |        }
             |      }
             |""".stripMargin
      }
      s"""
         |  messageDocs += ${methodName}Doc
         |  def ${methodName}Doc = MessageDoc(
         |    process = "obp.$methodName",
         |    messageFormat = messageFormat,
         |    description = "$description",
         |    outboundTopic = $outBoundTopic,
         |    inboundTopic = $inBoundTopic,
         |    exampleOutboundMessage = (
         |    $outBoundExample
         |    ),
         |    exampleInboundMessage = (
         |    $inBoundExample
         |    ),
         |    adapterImplementation = Some(AdapterImplementation("- Core", 1))
         |  )
         |
         |  override def $signature = {
         |    $body
         |  }
          """.stripMargin
    }
  }

  // commonMethodNames is now dynamically computed — see definition after excludeMethods below

  private def hasOutBoundInBoundDTO(methodName: String): Boolean = {
    try {
      Class.forName(s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}")
      Class.forName(s"com.openbankproject.commons.dto.InBound${methodName.capitalize}")
      true
    } catch {
      case _: ClassNotFoundException =>
        logger.debug(s"Method $methodName excluded: missing OutBound/InBound DTO")
        false
    }
  }

  /**
   * Unified exclude list — merges the former `specialMethods` and `omitMethods`.
   * Methods listed here are excluded from dynamic `commonMethodNames` computation.
   * Each entry is annotated with the reason for exclusion to guide future removal.
   */
  val excludeMethods: List[String] = List(
    // ── Group 1: Non-standard parameter / return types (formerly specialMethods) ──
    "getStatus",                          // Returns enum TransactionRequestStatus.Value, non-standard return type
    "createOrUpdateBranch",               // Takes BranchT trait param, non-standard parameter type
    "createOrUpdateBank",                 // Returns Box[Bank], non-standard return type
    "createOrUpdateAtm",                  // Takes AtmT trait param, non-standard parameter type
    "createOrUpdateProduct",              // Too many parameters, non-standard signature
    "createOrUpdateFXRate",               // Returns Box[FXRate], non-standard return type
    "getCurrentFxRate",                   // Returns Box[FXRate], non-standard return type
    "getCounterpartyFromTransaction",     // Non-standard signature
    "getCounterpartiesFromTransaction",   // Non-standard signature

    // ── Group 2: Should not be auto-generated (attribute definitions, standing orders) ──
    "createOrUpdateAttributeDefinition",  // Attribute definition method, should not be auto-generated
    "deleteAttributeDefinition",          // Attribute definition method, should not be auto-generated
    "getAttributeDefinition",             // Attribute definition method, should not be auto-generated
    "createStandingOrder",                // Standing order method, should not be auto-generated

    // ── Group 3: Dynamic entity / endpoint methods ──
    "dynamicEntityProcess",               // Dynamic entity, should not be auto-generated
    "dynamicEndpointProcess",             // Dynamic endpoint, should not be auto-generated
    "createDynamicEndpoint",              // Dynamic endpoint, should not be auto-generated
    "getDynamicEndpoint",                 // Dynamic endpoint, should not be auto-generated
    "getDynamicEndpoints",                // Dynamic endpoint, should not be auto-generated

    // ── Group 4: Legacy methods ──
    "getBankAccountByRoutingLegacy",      // Legacy method, also filtered by name suffix

    // ── Group 5: Missing standard OutBound/InBound DTO ──
    "getAccountRoutingsByScheme",         // Missing standard OutBound/InBound DTO
    "getAccountRouting",                  // Missing standard OutBound/InBound DTO
    "getBankAccountsWithAttributes",      // Missing standard OutBound/InBound DTO
    "getBankSettlementAccounts",          // Missing standard OutBound/InBound DTO
    "getCountOfTransactionsFromAccountToCounterparty", // Missing standard OutBound/InBound DTO
    "getAllAtms",                          // Missing standard OutBound/InBound DTO
    "getCurrentCurrencies",               // Missing standard OutBound/InBound DTO
    "getAgents",                          // Missing standard OutBound/InBound DTO
    "getCustomersAtAllBanks",             // Missing standard OutBound/InBound DTO
    "createOrUpdateBankAttribute",        // Missing standard OutBound/InBound DTO
    "getBankAttribute",                   // Missing standard OutBound/InBound DTO
    "createOrUpdateAtmAttribute",         // Missing standard OutBound/InBound DTO
    "getAtmAttribute",                    // Missing standard OutBound/InBound DTO
    "getBankAttributeById",               // Missing standard OutBound/InBound DTO
    "getAtmAttributeById",                // Missing standard OutBound/InBound DTO
    "getUserAttributes",                  // Missing standard OutBound/InBound DTO
    "getPersonalUserAttributes",          // Missing standard OutBound/InBound DTO
    "getNonPersonalUserAttributes",       // Missing standard OutBound/InBound DTO
    "getUserAttributesByUsers",           // Missing standard OutBound/InBound DTO
    "createOrUpdateUserAttribute",        // Missing standard OutBound/InBound DTO
    "getUserAttribute",                   // Missing standard OutBound/InBound DTO
    "getUserAttributeById",               // Missing standard OutBound/InBound DTO
    "deleteUserAttribute",                // Missing standard OutBound/InBound DTO
    "getTransactionRequestIdsByAttributeNameValues", // Missing standard OutBound/InBound DTO
    "sendCustomerNotification",           // Missing standard OutBound/InBound DTO
    "getAtmAttributesByAtm",              // Missing standard OutBound/InBound DTOst

    // ── Group 6: Scala / Java built-in methods ──
    "equals",                             // Scala/Java built-in method
    "==",                                 // Scala operator
    "!=",                                 // Scala operator
  ).distinct

  /**
   * Dynamically computed list of Connector method names eligible for code generation.
   * Replaces the former hardcoded ~140-entry list.
   *
   * Pipeline:
   *  1. Start from all OBP-compatible Connector methods (via reflection)
   *  2. Filter out Legacy methods (names ending with "Legacy")
   *  3. Filter out excludeMethods entries
   *  4. Keep only methods that have both OutBound and InBound DTOs
   *  5. Deduplicate and sort for stable, reviewable output
   */
  val commonMethodNames: List[String] = connectorDeclsMethodsReturnOBPRequiredType
    .map(_.name.toString)
    .filterNot(_.endsWith("Legacy"))
    .filterNot(excludeMethods.contains(_))
    .filter(hasOutBoundInBoundDTO)
    .toList
    .distinct
    .sorted
}


