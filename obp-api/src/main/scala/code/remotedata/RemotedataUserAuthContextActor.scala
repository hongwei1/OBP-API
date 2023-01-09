package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.context.{MappedUserAuthContextProvider, RemotedataUserAuthContextCaseClasses}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.BasicUserAuthContext

import scala.collection.immutable.List

class RemotedataUserAuthContextActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserAuthContextProvider
  val cc = RemotedataUserAuthContextCaseClasses

  def receive = {

    case cc.createUserAuthContext(userId: String, key: String, value: String, consumerId: String) =>
      logger.debug(s"createUserAuthContext($userId, $key, $value, $consumerId)")
      sender ! (mapper.createUserAuthContextAkka(userId, key, value, consumerId))

    case cc.getUserAuthContexts(userId: String) =>
      logger.debug(s"getUserAuthContexts($userId)")
      sender ! (mapper.getUserAuthContextsBox(userId))
      
    case cc.getUserAuthContextsBox(userId: String) =>
      logger.debug(s"getUserAuthContextsBox($userId)")
      sender ! (mapper.getUserAuthContextsBox(userId))   
      
    case cc.createOrUpdateUserAuthContexts(userId: String, userAuthContexts: List[BasicUserAuthContext]) =>
      logger.debug(s"createOrUpdateUserAuthContexts($userId, $userAuthContexts)")
      sender ! (mapper.createOrUpdateUserAuthContexts(userId, userAuthContexts))
      
    case cc.deleteUserAuthContexts(userId: String) =>
      logger.debug(msg=s"deleteUserAuthContexts(${userId})")
      sender ! (mapper.deleteUserAuthContextsAkka(userId))

    case cc.deleteUserAuthContextById(userAuthContextId: String) =>
      logger.debug(msg=s"deleteUserAuthContextById(${userAuthContextId})")
      sender ! (mapper.deleteUserAuthContextByIdAkka(userAuthContextId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


