package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.context.{RemotedataUserAuthContextCaseClasses, UserAuthContextProvider}
import com.openbankproject.commons.model.{BasicUserAuthContext, UserAuthContext}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataUserAuthContext extends ObpActorInit with UserAuthContextProvider {

  val cc = RemotedataUserAuthContextCaseClasses

  def getUserAuthContexts(userId: String): Future[Box[List[UserAuthContext]]] =
    (actor ? cc.getUserAuthContexts(userId)).mapTo[Box[List[UserAuthContext]]]
  
  def getUserAuthContextsBox(userId: String): Box[List[UserAuthContext]] = getValueFromFuture(
    (actor ? cc.getUserAuthContextsBox(userId)).mapTo[Box[List[UserAuthContext]]]
  )  
  def createOrUpdateUserAuthContexts(userId: String, userAuthContexts: List[BasicUserAuthContext]): Box[List[UserAuthContext]] = getValueFromFuture(
    (actor ? cc.createOrUpdateUserAuthContexts(userId, userAuthContexts)).mapTo[Box[List[UserAuthContext]]]
  )

  def createUserAuthContext(userId: String, key: String, value: String, consumerId: String): Future[Box[UserAuthContext]] =
    (actor ? cc.createUserAuthContext(userId, key, value, consumerId)).mapTo[Box[UserAuthContext]]

  override def deleteUserAuthContexts(userId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteUserAuthContexts(userId)).mapTo[Box[Boolean]]

  override def deleteUserAuthContextById(userAuthContextId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteUserAuthContextById(userAuthContextId)).mapTo[Box[Boolean]]
}
