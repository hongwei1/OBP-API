package code.users

import java.util.Date

import code.model.dataAccess.ResourceUser
import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.{BankId, UserMessageTrait, User}
import net.liftweb.mapper._

object MappedUserMessageProvider extends UserMessageProvider {

  override def getMessages(user: User, bankId : BankId): List[UserMessageTrait] = {
    UserMessage.findAll(
      By(UserMessage.user, user.userPrimaryKey.value),
      By(UserMessage.bank, bankId.value),
      OrderBy(UserMessage.updatedAt, Descending))
  }


  override def createMessage(user: User, bankId: BankId, message: String, fromDepartment: String, fromPerson: String, transport: String) = {
    UserMessage.create
      .FromDepartment(fromDepartment)
      .FromPerson(fromPerson)
      .Message(message)
      .user(user.userPrimaryKey.value)
      .bank(bankId.value).saveMe()
  }
}

class UserMessage extends UserMessageTrait
  with LongKeyedMapper[UserMessage] with IdPK with CreatedUpdated {

  def getSingleton = UserMessage

  object user extends MappedLongForeignKey(this, ResourceUser)
  object bank extends UUIDString(this)

  object FromPerson extends MappedString(this, 64)
  object FromDepartment extends MappedString(this, 64)
  object Message extends MappedString(this, 1024)
  object MessageId extends MappedUUID(this)
  object Transport extends MappedUUID(this)


  override def messageId: String = MessageId.get
  override def date: Date = createdAt.get
  override def fromPerson: String = FromPerson.get
  override def fromDepartment: String = FromDepartment.get
  override def message: String = Message.get
  override def transport: Option[String] = if (Transport.get == null || Transport.get.isEmpty) None else Some(Transport.get)
}

object UserMessage extends UserMessage with LongKeyedMetaMapper[UserMessage] {
  override def dbIndexes = UniqueIndex(MessageId) :: super.dbIndexes
}