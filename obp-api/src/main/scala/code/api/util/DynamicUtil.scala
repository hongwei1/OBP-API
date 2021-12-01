package code.api.util
import code.api.JsonResponseException
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages.DynamicResourceDocMethodDependency
import code.api.util.NewStyle.HttpCode
import code.api.v4_0_0.JSONFactory400
import code.api.v4_0_0.dynamic.{CompiledObjects, DynamicCompileEndpoint}
import code.api.v4_0_0.dynamic.practise.PractiseEndpoint
import com.openbankproject.commons.ExecutionContext
import com.openbankproject.commons.model.{Bank, BankId}
import com.openbankproject.commons.util.Functions.Memo
import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
import javassist.{ClassPool, LoaderClassPath}
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.http.JsonResponse
import net.liftweb.json.{JValue, prettyRender}
import org.apache.commons.lang3.StringUtils

import java.io.FilePermission
import java.lang.reflect.ReflectPermission
import java.net.NetPermission
import java.security.{AccessControlContext, AccessController, CodeSource, Permission, PermissionCollection, Permissions, Policy, PrivilegedAction, ProtectionDomain}
import java.util.PropertyPermission
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.runtimeMirror
import scala.runtime.NonLocalReturnControl
import scala.tools.reflect.{ToolBox, ToolBoxError}

object DynamicUtil {

  val toolBox: ToolBox[universe.type] = runtimeMirror(getClass.getClassLoader).mkToolBox()
  private val memoClassPool = new Memo[ClassLoader, ClassPool]

  private def getClassPool(classLoader: ClassLoader) = memoClassPool.memoize(classLoader){
    val cp = ClassPool.getDefault
    cp.appendClassPath(new LoaderClassPath(classLoader))
    cp
  }

  // code -> dynamic method function
  // the same code should always be compiled once, so here cache them
  private val dynamicCompileResult = new ConcurrentHashMap[String, Box[Any]]()
  /**
   * Compile scala code
   * toolBox have bug that first compile fail, second or later compile success.
   * @param code
   * @return compiled Full[function|object|class] or Failure
   */
  def compileScalaCode[T](code: String): Box[T] = {
    val compiledResult: Box[Any] = dynamicCompileResult.computeIfAbsent(code, _ => {
      val tree = try {
        toolBox.parse(code)
      } catch {
        case e: ToolBoxError =>
          return Failure(e.message)
      }

      try {
        val func: () => Any = toolBox.compile(tree)
        Box.tryo(func())
      } catch {
        case _: ToolBoxError =>
          // try compile again
          try {
            val func: () => Any = toolBox.compile(tree)
            Box.tryo(func())
          } catch {
            case e: ToolBoxError =>
              Failure(e.message)
          }
      }
    })

    compiledResult.map(_.asInstanceOf[T])
  }

  /**
   * 
   * @param methodName the method name
   * @param function the method body, if it is empty, then throw exception. if it is existing, then call this function.
   * @param args the method parameters
   * @return the result of the execution of the function.
   */
  def executeFunction(methodName: String, function: Box[Any], args: Array[AnyRef]) = {
    val result = function.orNull match {
      case func: Function0[AnyRef] => func()
      case func: Function[AnyRef, AnyRef] => func(args.head)
      case func: Function2[AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1))
      case func: Function3[AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2))
      case func: Function4[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3))
      case func: Function5[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4))
      case func: Function6[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5))
      case func: Function7[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6))
      case func: Function8[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7))
      case func: Function9[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8))
      case func: Function10[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9))
      case func: Function11[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10))
      case func: Function12[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11))
      case func: Function13[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12))
      case func: Function14[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13))
      case func: Function15[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14))
      case func: Function16[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15))
      case func: Function17[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16))
      case func: Function18[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17))
      case func: Function19[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17), args.apply(18))
      case func: Function20[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17), args.apply(18), args.apply(19))
      case null => throw new IllegalStateException(s"There is  no method $methodName, it should not be called here")
      case _ => throw new IllegalStateException(s"$methodName can not be called here.")
    }
    result.asInstanceOf[AnyRef]
  }

  /**
   * this method will create a object from the JValue.
   * from JValue --> Case Class String -->  DynamicUtil.compileScalaCode(code) --> object 
   * @param jValue
   * @return 
   */
  def toCaseObject(jValue: JValue): Product = {
    val caseClasses = JsonUtils.toCaseClasses(jValue)
    val code =
      s"""
         | $caseClasses
         |
         | // throws exception: net.liftweb.json.MappingException:
         | //No usable value for name
         | //Did not find value which can be converted into java.lang.String
         |
         |implicit val formats = code.api.util.CustomJsonFormats.formats
         |(jValue: net.liftweb.json.JsonAST.JValue) => {
         |  jValue.extract[RootJsonClass]
         |}
         |""".stripMargin
    val fun: Box[JValue => Product] = DynamicUtil.compileScalaCode(code)
    fun match {
      case Full(func) => func.apply(jValue)
      case Failure(msg: String, exception: Box[Throwable], _) =>
        throw exception.getOrElse(new RuntimeException(msg))
      case _ => throw new RuntimeException(s"Json extract to case object fail, json: \n ${prettyRender(jValue)}")
    }
  }

  def getDynamicCodeDependentMethods(clazz: Class[_], predicate:  String => Boolean = _ => true): List[(String, String, String)] = {
    val className = clazz.getTypeName
    val listBuffer = new ListBuffer[(String, String, String)]()
    for {
      method <- getClassPool(clazz.getClassLoader).get(className).getDeclaredMethods.toList
      if predicate(method.getName)
      ternary @ (typeName, methodName, signature) <- APIUtil.getDependentMethods(className, method.getName, method.getSignature)
    } yield {
      // if method is also dynamic compile code, extract it's dependent method
      if(className.startsWith(typeName) && methodName.startsWith(clazz.getPackage.getName+ "$")) {
        listBuffer.appendAll(APIUtil.getDependentMethods(typeName, methodName, signature))
      } else {
        listBuffer.append(ternary)
      }
    }

    listBuffer.distinct.toList
  }

  trait Sandbox {
    @throws[Exception]
    def runInSandbox[R](action: => R): R
  }

  object Sandbox {
    // initialize SecurityManager if not initialized
    if (System.getSecurityManager == null) {
      Policy.setPolicy(new Policy() {
        override def getPermissions(codeSource: CodeSource): PermissionCollection = {
          for (element <- Thread.currentThread.getStackTrace) {
            if ("sun.rmi.server.LoaderHandler" == element.getClassName && "loadClass" == element.getMethodName)
              return new Permissions
          }
          super.getPermissions(codeSource)
        }

        override def implies(domain: ProtectionDomain, permission: Permission) = true
      })
      System.setSecurityManager(new SecurityManager)
    }

    def createSandbox(permissionList: List[Permission]): Sandbox = {
      val accessControlContext: AccessControlContext = {
        val permissions = new Permissions()
        permissionList.foreach(permissions.add)
        val protectionDomain = new ProtectionDomain(null, permissions)
        new AccessControlContext(Array(protectionDomain))
      }

      new Sandbox {
        @throws[Exception]
        def runInSandbox[R](action: => R): R = try {
          val privilegedAction:  PrivilegedAction[R] = () => action

          AccessController.doPrivileged(privilegedAction, accessControlContext)
        } catch {
          case  e: NonLocalReturnControl[Full[JsonResponse]] if e.value.isInstanceOf[Full[JsonResponse]] =>
            throw JsonResponseException(e.value.orNull)

          case e: NonLocalReturnControl[JsonResponse] if e.value.isInstanceOf[JsonResponse] =>
            throw JsonResponseException(e.value)
        }
      }
    }

    private val memoSandbox = new Memo[String, Sandbox]
    def sandbox(bankId: String): Sandbox = memoSandbox.memoize(bankId) {
      Sandbox.createSandbox(BankId.permission(bankId) :: Validation.allowedPermissions)
    }
  }

  /**
   * common import statements those are used by compiler
   */
 val importStatements =
    """
      |import java.net.{ConnectException, URLEncoder, UnknownHostException}
      |import java.util.Date
      |import java.util.UUID.randomUUID
      |
      |import _root_.akka.stream.StreamTcpException
      |import akka.http.scaladsl.model.headers.RawHeader
      |import akka.http.scaladsl.model.{HttpProtocol, _}
      |import akka.util.ByteString
      |import code.api.APIFailureNewStyle
      |import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions
      |import code.api.cache.Caching
      |import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, saveConnectorMetric, _}
      |import code.api.util.ErrorMessages._
      |import code.api.util.ExampleValue._
      |import code.api.util.{APIUtil, CallContext, OBPQueryParam}
      |import code.api.v4_0_0.dynamic.MockResponseHolder
      |import code.bankconnectors._
      |import code.bankconnectors.vJune2017.AuthInfo
      |import code.customer.internalMapping.MappedCustomerIdMappingProvider
      |import code.kafka.KafkaHelper
      |import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
      |import code.util.AkkaHttpClient._
      |import code.util.Helper.MdcLoggable
      |import com.openbankproject.commons.dto.{InBoundTrait, _}
      |import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
      |import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, DynamicEntityOperation, ProductAttributeType}
      |import com.openbankproject.commons.model.{ErrorMessage, TopicTrait, _}
      |import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
      |// import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
      |import net.liftweb.common.{Box, Empty, _}
      |import net.liftweb.json
      |import net.liftweb.json.Extraction.decompose
      |import net.liftweb.json.JsonDSL._
      |import net.liftweb.json.JsonParser.ParseException
      |import net.liftweb.json.{JValue, _}
      |import net.liftweb.util.Helpers.tryo
      |import org.apache.commons.lang3.StringUtils
      |
      |import scala.collection.immutable.List
      |import scala.collection.mutable.ArrayBuffer
      |import scala.concurrent.duration._
      |import scala.concurrent.{Await, Future}
      |import com.openbankproject.commons.dto._
      |""".stripMargin


  object Validation {
    // all Permissions put at here
   // https://docs.oracle.com/javase/8/docs/technotes/guides/security/spec/security-spec.doc3.html#17001
    //Here is the Runtime
    val allowedPermissions= List[Permission](
      new NetPermission("specifyStreamHandler"),
      new ReflectPermission("suppressAccessChecks"),
      new RuntimePermission("getenv.*"),
      new PropertyPermission("cglib.useCache", "read"),
      new PropertyPermission("net.sf.cglib.test.stressHashCodes", "read"),
      new PropertyPermission("cglib.debugLocation", "read"),
      new RuntimePermission("accessDeclaredMembers"),
      new RuntimePermission("getClassLoader"),
      new FilePermission("stop-words-en.txt","write")
//    val out = new java.io.FileWriter("stop-words-en.txt")
    )

    // all allowed methods put at here, typeName -> methods
    // only for the complation, in our OBP code there is no security check, we need to check it manully!!!
    val allowedMethods: Map[String, Set[String]] = Map(
      // companion objects methods
      NewStyle.function.getClass.getTypeName -> "*",
      CompiledObjects.getClass.getTypeName -> "sandbox",
      HttpCode.getClass.getTypeName -> "200",
      DynamicCompileEndpoint.getClass.getTypeName -> "getPathParams, scalaFutureToBoxedJsonResponse",
      APIUtil.getClass.getTypeName -> "errorJsonResponse, errorJsonResponse$default$1, errorJsonResponse$default$2, errorJsonResponse$default$3, errorJsonResponse$default$4, scalaFutureToLaFuture, futureToBoxedResponse",
      ErrorMessages.getClass.getTypeName -> "*",
      ExecutionContext.Implicits.getClass.getTypeName -> "global",
      JSONFactory400.getClass.getTypeName -> "createBanksJson, createBankJSON400, createBankJSON400$default$1, createBankJSON400$default$2",

      // class methods
      classOf[Sandbox].getTypeName -> "runInSandbox",
      classOf[CallContext].getTypeName -> "*",
      classOf[ResourceDoc].getTypeName -> "getPathParams",
      "scala.reflect.runtime.package$" -> "universe",

      // allow any method of PractiseEndpoint for test
      PractiseEndpoint.getClass.getTypeName + "*" -> "*",

    ).mapValues(v => StringUtils.split(v, ',').map(_.trim).toSet)

    val restrictedTypes = Set(
      "scala.reflect.runtime.",
      "java.lang.reflect.",
      "scala.concurrent.ExecutionContext"
    )

    private def isRestrictedType(typeName: String) = ReflectUtils.isObpClass(typeName) || restrictedTypes.exists(typeName.startsWith)

    /**
     * validate dependencies, (className, methodName, signature)
     */
    private def validateDependency(dependentMethods: List[(String, String, String)]) = {
      val notAllowedDependentMethods = dependentMethods collect {
        case (typeName, method, _)
          if isRestrictedType(typeName) &&
            !allowedMethods.get(typeName).exists(set => set.contains(method) || set.contains("*")) &&
            !allowedMethods.exists { it =>
              val (tpName, allowedMethods) = it
              tpName.endsWith("*") &&
                typeName.startsWith(StringUtils.substringBeforeLast(tpName, "*")) &&
                (allowedMethods.contains(method) || allowedMethods.contains("*"))
            }
        =>
          s"$typeName.$method"
      }
      // change to JsonResponseException
      if(notAllowedDependentMethods.nonEmpty) {
        val illegalDependency = notAllowedDependentMethods.mkString("[", ", ", "]")
        throw JsonResponseException(s"$DynamicResourceDocMethodDependency $illegalDependency", 400, "none")
      }
    }

    def validateDependency(obj: AnyRef): Unit = {
      val dependentMethods: List[(String, String, String)] = DynamicUtil.getDynamicCodeDependentMethods(obj.getClass)
      validateDependency(dependentMethods)
    }
  }
}




object AkkaConnectorBuilder3 extends App {
  println(123123)
  import sys.process._
  import java.net.URL
  import java.io.File

  def fileDownloader(url: String, filename: String) = {
    new URL(url) #> new File(filename) !!
  }
  val abc = fileDownloader("http://ir.dcs.gla.ac.uk/resources/linguistic_utils/stop_words", "stop-words-en.txt")

}