package code.metadata.counterparties

import java.util.{Date, UUID}

import code.api.util.APIUtil
import code.api.util.APIUtil.getSecondsCache
import code.api.v2_1_0.PostCounterpartyBespoke
import code.model._
import code.model.dataAccess.ResourceUser
import code.users.Users
import code.util._
import net.liftweb.common.{Box, Full}
import code.util.Helper.MdcLoggable
import com.google.common.cache.CacheBuilder
import net.liftweb.mapper.{By, MappedString, _}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scalacache.ScalaCache
import scalacache.guava.GuavaCache
import scalacache.memoization.memoizeSync
import scala.concurrent.duration._

// For now, there are two Counterparties: one is used for CreateCounterParty.Counterparty, the other is for getTransactions.Counterparty.
// 1st is created by app explicitly, when use `CreateCounterParty` endpoint. This will be stored in database .
// 2nd is generated by obp implicitly, when use `getTransactions` endpoint. This will not be stored in database, but we create the CounterpartyMetadata for it. And the CounterpartyMetadata is in database. 
// They are relevant somehow, but they are different data for now. Both data can be get by the following `MapperCounterparties` object. 
object MapperCounterparties extends Counterparties with MdcLoggable {
  
  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))
  val MetadataTTL = getSecondsCache("getOrCreateMetadata")
  
  override def getOrCreateMetadata(bankId: BankId, accountId: AccountId, counterpartyId: String, counterpartyName:String): Box[CounterpartyMetadata] =  memoizeSync(MetadataTTL second){

    /**
     * Generates a new alias name that is guaranteed not to collide with any existing public alias names
     * for the account in question
     */
    def newPublicAliasName(): String = {
      val firstAliasAttempt = "ALIAS_" + UUID.randomUUID.toString.toUpperCase.take(6)
  
      val counterpartyMetadatasPublicAlias = MappedCounterpartyMetadata
        .findAll(
          By(MappedCounterpartyMetadata.thisBankId, bankId.value), 
          By(MappedCounterpartyMetadata.thisAccountId, accountId.value))
        .map(_.addPublicAlias)
      
      def isDuplicate(publicAlias: String) = counterpartyMetadatasPublicAlias.contains(publicAlias)

      /**
       * Appends things to @publicAlias until it a unique public alias name within @account
       */
      def appendUntilUnique(publicAlias: String): String = {
        val newAlias = publicAlias + UUID.randomUUID.toString.toUpperCase.take(1)
        // Recursive call.
        if (isDuplicate(newAlias)) appendUntilUnique(newAlias)
        else newAlias
      }

      if (isDuplicate(firstAliasAttempt)) appendUntilUnique(firstAliasAttempt)
      else firstAliasAttempt
    }

    def findMappedCounterpartyMetadataById(counterpartyId: String) = MappedCounterpartyMetadata.find(By(MappedCounterpartyMetadata.counterpartyId, counterpartyId))
    
    findMappedCounterpartyMetadataById(counterpartyId) match {
      case Full(e) =>
        logger.debug(s"getOrCreateMetadata--Get MappedCounterpartyMetadata counterpartyId($counterpartyId)")
        Full(e)
      // Create it!
      case _ => {
        logger.debug(s"getOrCreateMetadata--Create MappedCounterpartyMetadata counterpartyId($counterpartyId)")
        // Store a record that contains counterparty information from the perspective of an account at a bank
        Full(MappedCounterpartyMetadata.create
          // Core info
          .counterpartyId(counterpartyId)
          .thisBankId(bankId.value)
          .thisAccountId(accountId.value)
          .counterpartyName(counterpartyName) 
          .publicAlias(newPublicAliasName()) // The public alias this account gives to the counterparty.
          .saveMe)
      }
    }
  }

  // Get all counterparty metadata for a single OBP account
  override def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId): List[CounterpartyMetadata] = {
    MappedCounterpartyMetadata.findAll(
      By(MappedCounterpartyMetadata.thisBankId, originalPartyBankId.value),
      By(MappedCounterpartyMetadata.thisAccountId, originalPartyAccountId.value)
    )
  }

  override def getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[CounterpartyMetadata] = {
    /**
     * This particular implementation requires the metadata id to be the same as the otherParty (OtherBankAccount) id
     */
    MappedCounterpartyMetadata.find(
      By(MappedCounterpartyMetadata.thisBankId, originalPartyBankId.value),
      By(MappedCounterpartyMetadata.thisAccountId, originalPartyAccountId.value),
      By(MappedCounterpartyMetadata.counterpartyId, counterpartyMetadataId)
    )
  }

  def addMetadata(bankId: BankId, accountId : AccountId): Box[CounterpartyMetadata] = {
    Full(
    MappedCounterpartyMetadata.create
      .thisBankId(bankId.value)
      .thisAccountId(accountId.value)
      .saveMe
    )
  }


  override def getCounterparty(counterPartyId : String): Box[CounterpartyTrait] = {
    MappedCounterparty.find(By(MappedCounterparty.mCounterPartyId, counterPartyId))
  }
  override def getCounterpartyByIban(iban : String): Box[CounterpartyTrait] = {
    MappedCounterparty.find(
      By(MappedCounterparty.mOtherAccountRoutingAddress, iban),
      By(MappedCounterparty.mOtherAccountRoutingScheme, "IBAN")
    )
  }

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]] = {
    Full(MappedCounterparty.findAll(By(MappedCounterparty.mThisAccountId, thisAccountId.value),
      By(MappedCounterparty.mThisBankId, thisBankId.value),
      By(MappedCounterparty.mThisViewId, viewId.value)))
  }

  override def createCounterparty(
                                  createdByUserId: String,
                                  thisBankId: String,
                                  thisAccountId : String,
                                  thisViewId : String,
                                  name: String,
                                  otherAccountRoutingScheme : String,
                                  otherAccountRoutingAddress : String,
                                  otherBankRoutingScheme : String,
                                  otherBankRoutingAddress : String,
                                  otherBranchRoutingScheme: String,
                                  otherBranchRoutingAddress: String,
                                  isBeneficiary: Boolean,
                                  otherAccountSecondaryRoutingScheme: String,
                                  otherAccountSecondaryRoutingAddress: String,
                                  description: String,
                                  bespoke: List[PostCounterpartyBespoke]
                                 ): Box[CounterpartyTrait] = {
    
    val mappedCounterparty = MappedCounterparty.create
      .mCounterPartyId(APIUtil.createExplicitCounterpartyId) //We create the Counterparty_Id here, it means, it will be create in each connnector.
      .mName(name)
      .mCreatedByUserId(createdByUserId)
      .mThisBankId(thisBankId)
      .mThisAccountId(thisAccountId)
      .mThisViewId(thisViewId)
      .mOtherAccountRoutingScheme(otherAccountRoutingScheme)
      .mOtherAccountRoutingAddress(otherAccountRoutingAddress)
      .mOtherBankRoutingScheme(otherBankRoutingScheme)
      .mOtherBankRoutingAddress(otherBankRoutingAddress)
      .mOtherBranchRoutingAddress(otherBranchRoutingAddress)
      .mOtherBranchRoutingScheme(otherBranchRoutingScheme)
      .mIsBeneficiary(isBeneficiary)
      .mDescription(description)
      .mOtherAccountSecondaryRoutingScheme(otherAccountSecondaryRoutingScheme)
      .mOtherAccountSecondaryRoutingAddress(otherAccountSecondaryRoutingAddress)
      .saveMe()
  
    // This is especially for OneToMany table, to save a List to database.
    bespoke.map(mBespoke => MappedCounterpartyBespoke.create
      .mKey(mBespoke.key)
      .mVaule(mBespoke.value)
      .saveMe()
    ).map(
      mappedBespoke =>
        mappedCounterparty.mBespoke += mappedBespoke
    )
    
    Some(
      mappedCounterparty
    )
    
  }

 override def checkCounterpartyAvailable(
                               name: String,
                               thisBankId: String,
                               thisAccountId: String,
                               thisViewId: String
                             ): Boolean = {
   val counterparties = MappedCounterparty.findAll(
     By(MappedCounterparty.mName, name),
     By(MappedCounterparty.mThisBankId, thisBankId),
     By(MappedCounterparty.mThisAccountId, thisAccountId),
     By(MappedCounterparty.mThisViewId, thisViewId)
   )

   val available: Boolean = counterparties.size match {
     case 0 => true
     case _ => false
   }

   available
  }


  private def getCounterpartyMetadata(counterPartyId : String) : Box[MappedCounterpartyMetadata] = {
    MappedCounterpartyMetadata.find(By(MappedCounterpartyMetadata.counterpartyId, counterPartyId))
  }

  override def getPublicAlias(counterPartyId : String): Box[String] = {
    getCounterpartyMetadata(counterPartyId).map(_.publicAlias.get)
  }

  override def getPrivateAlias(counterPartyId : String): Box[String] = {
    getCounterpartyMetadata(counterPartyId).map(_.privateAlias.get)
  }

  override def getPhysicalLocation(counterPartyId : String): Box[GeoTag] = {
    getCounterpartyMetadata(counterPartyId).flatMap(_.physicalLocation.obj)
  }

  override def getOpenCorporatesURL(counterPartyId : String): Box[String] = {
    getCounterpartyMetadata(counterPartyId).map(_.getOpenCorporatesURL)
  }

  override def getImageURL(counterPartyId : String): Box[String] = {
    getCounterpartyMetadata(counterPartyId).map(_.getImageURL)
  }

  override def getUrl(counterPartyId : String): Box[String] = {
    getCounterpartyMetadata(counterPartyId).map(_.getUrl)
  }

  override def getMoreInfo(counterPartyId : String): Box[String] = {
    getCounterpartyMetadata(counterPartyId).map(_.getMoreInfo)
  }

  override def getCorporateLocation(counterPartyId : String): Box[GeoTag] = {
    getCounterpartyMetadata(counterPartyId).flatMap(_.corporateLocation.obj)
  }

  override def addPublicAlias(counterPartyId : String, alias: String): Box[Boolean] = {
    getCounterpartyMetadata(counterPartyId).map(_.publicAlias(alias).save())
  }

  override def addPrivateAlias(counterPartyId : String, alias: String): Box[Boolean] = {
    getCounterpartyMetadata(counterPartyId).map(_.privateAlias(alias).save())
  }

  override def addURL(counterPartyId : String, url: String): Box[Boolean] = {
    getCounterpartyMetadata(counterPartyId).map(_.url(url).save())
  }

  override def addImageURL(counterPartyId : String, url: String): Box[Boolean] = {
    getCounterpartyMetadata(counterPartyId).map(_.imageUrl(url).save())
  }

  override def addOpenCorporatesURL(counterPartyId : String, url: String): Box[Boolean] = {
    getCounterpartyMetadata(counterPartyId).map(_.openCorporatesUrl(url).save())
  }

  override def addMoreInfo(counterPartyId : String, moreInfo: String): Box[Boolean] = {
    getCounterpartyMetadata(counterPartyId).map(_.moreInfo(moreInfo).save())
  }

  override def addPhysicalLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] = {
    getCounterpartyMetadata(counterPartyId).map(_.setPhysicalLocation(userId, datePosted, longitude, latitude))
  }

  override def addCorporateLocation(counterPartyId : String, userId: UserId, datePosted : Date, longitude : Double, latitude : Double): Box[Boolean] = {
    getCounterpartyMetadata(counterPartyId).map(_.setCorporateLocation(userId, datePosted, longitude, latitude))
  }

  override def deletePhysicalLocation(counterPartyId : String): Box[Boolean] = {
    getCounterpartyMetadata(counterPartyId).flatMap(_.physicalLocation.obj).map(_.delete_!)
  }

  override def deleteCorporateLocation(counterPartyId : String): Box[Boolean] = {
    getCounterpartyMetadata(counterPartyId).flatMap(_.corporateLocation.obj).map(_.delete_!)
  }
}


// for now, there are two Counterparties: one is used for CreateCounterParty.Counterparty, the other is for getTransactions.Counterparty.
// 1st is created by app explicitly, when use `CreateCounterParty` endpoint. This will be stored in database .
// 2nd is generated by obp implicitly, when use `getTransactions` endpoint. This will not be stored in database, but we create the CounterpartyMetadata for it. And the CounterpartyMetadata is in database. 
// They are relevant somehow, but they are different data for now.

class MappedCounterpartyMetadata extends CounterpartyMetadata with LongKeyedMapper[MappedCounterpartyMetadata] with IdPK with CreatedUpdated {
  override def getSingleton = MappedCounterpartyMetadata

  //these define the counterparty, not metadata
  object counterpartyId extends UUIDString(this)
  object counterpartyName extends MappedString(this, 255) 

  //these define the obp account to which this counterparty belongs
  object thisBankId extends UUIDString(this)
  object thisAccountId extends AccountIdString(this)


  //this is the counterparty's metadata
  object publicAlias extends MappedString(this, 64)
  object privateAlias extends MappedString(this, 64)
  object moreInfo extends MappedString(this, 255)
  object url extends MappedString(this, 2000)
  object imageUrl extends MappedString(this, 2000)
  object openCorporatesUrl extends MappedString(this, 2000)

  object physicalLocation extends MappedLongForeignKey(this, MappedCounterpartyWhereTag)
  object corporateLocation extends MappedLongForeignKey(this, MappedCounterpartyWhereTag)

  /**
   * Evaluates f, and then attempts to save. If no exceptions are thrown and save executes successfully,
   * true is returned. If an exception is thrown or if the save fails, false is returned.
   * @param f the expression to evaluate (e.g. imageUrl("http://example.com/foo.png")
   * @return If saving the model worked after having evaluated f
   */
  private def trySave(f : => Any) : Boolean =
    tryo{
      f
      save()
    }.getOrElse(false)

  private def setWhere(whereTag : Box[MappedCounterpartyWhereTag])
                      (userId: UserId, datePosted : Date, longitude : Double, latitude : Double) : Box[MappedCounterpartyWhereTag] = {
    val toUpdate = whereTag match {
      case Full(c) => c
      case _ => MappedCounterpartyWhereTag.create
    }

    tryo{
      toUpdate
        .user(userId.value)
        .date(datePosted)
        .geoLongitude(longitude)
        .geoLatitude(latitude)
        .saveMe
    }
  }

  def setCorporateLocation(userId: UserId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    //save where tag
    val savedWhere = setWhere(corporateLocation.obj)(userId, datePosted, longitude, latitude)
    //set where tag for counterparty
    savedWhere.map(location => trySave{corporateLocation(location)}).getOrElse(false)
  }

  def setPhysicalLocation(userId: UserId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    //save where tag
    val savedWhere = setWhere(physicalLocation.obj)(userId, datePosted, longitude, latitude)
    //set where tag for counterparty
    savedWhere.map(location => trySave{physicalLocation(location)}).getOrElse(false)
  }

  override def getCounterpartyId: String = counterpartyId.get
  override def getCounterpartyName: String = counterpartyName.get
  override def getPublicAlias: String = publicAlias.get
  override def getCorporateLocation: Option[GeoTag] =
    corporateLocation.obj
  override def getOpenCorporatesURL: String = openCorporatesUrl.get
  override def getMoreInfo: String = moreInfo.get
  override def getPrivateAlias: String = privateAlias.get
  override def getImageURL: String = imageUrl.get
  override def getPhysicalLocation: Option[GeoTag] =
    physicalLocation.obj
  override def getUrl: String = url.get

  override val addPhysicalLocation: (UserId, Date, Double, Double) => Boolean = setPhysicalLocation _
  override val addCorporateLocation: (UserId, Date, Double, Double) => Boolean = setCorporateLocation _
  override val addPrivateAlias: (String) => Boolean = (x) =>
    trySave{privateAlias(x)}
  override val addURL: (String) => Boolean = (x) =>
    trySave{url(x)}
  override val addMoreInfo: (String) => Boolean = (x) =>
    trySave{moreInfo(x)}
  override val addPublicAlias: (String) => Boolean = (x) =>
    trySave{publicAlias(x)}
  override val addOpenCorporatesURL: (String) => Boolean = (x) =>
    trySave{openCorporatesUrl(x)}
  override val addImageURL: (String) => Boolean = (x) =>
    trySave{imageUrl(x)}
  override val deleteCorporateLocation = () =>
    corporateLocation.obj.map(_.delete_!).getOrElse(false)
  override val deletePhysicalLocation = () =>
    physicalLocation.obj.map(_.delete_!).getOrElse(false)

}

object MappedCounterpartyMetadata extends MappedCounterpartyMetadata with LongKeyedMetaMapper[MappedCounterpartyMetadata] {
  override def dbIndexes =
    UniqueIndex(counterpartyId) ::
    super.dbIndexes
}

class MappedCounterpartyWhereTag extends GeoTag with LongKeyedMapper[MappedCounterpartyWhereTag] with IdPK with CreatedUpdated {

  def getSingleton = MappedCounterpartyWhereTag

  object user extends MappedLongForeignKey(this, ResourceUser)
  object date extends MappedDateTime(this)

  //TODO: require these to be valid latitude/longitudes
  object geoLatitude extends MappedDouble(this)
  object geoLongitude extends MappedDouble(this)

  override def postedBy: Box[User] = Users.users.vend.getUserByResourceUserId(user.get)
  override def datePosted: Date = date.get
  override def latitude: Double = geoLatitude.get
  override def longitude: Double = geoLongitude.get
}

object MappedCounterpartyWhereTag extends MappedCounterpartyWhereTag with LongKeyedMetaMapper[MappedCounterpartyWhereTag]




// for now, there are two Counterparties: one is used for CreateCounterParty.Counterparty, the other is for getTransactions.Counterparty.
// 1st is created by app explicitly, when use `CreateCounterParty` endpoint. This will be stored in database .
// 2nd is generated by obp implicitly, when use `getTransactions` endpoint. This will not be stored in database, but we create the CounterpartyMetadata for it. And the CounterpartyMetadata is in database. 
// They are relevant somehow, but they are different data for now.
class MappedCounterparty extends CounterpartyTrait with LongKeyedMapper[MappedCounterparty] with IdPK with CreatedUpdated with OneToMany[Long, MappedCounterparty] {
  def getSingleton = MappedCounterparty

  object mCreatedByUserId extends MappedString(this, 36)
  object mName extends MappedString(this, 36)
  object mThisBankId extends MappedString(this, 36)
  object mThisAccountId extends AccountIdString(this)
  object mThisViewId extends MappedString(this, 36)
  object mCounterPartyId extends UUIDString(this)
  object mOtherAccountRoutingScheme extends MappedString(this, 255)
  object mOtherAccountRoutingAddress extends MappedString(this, 255)
  object mOtherBankRoutingScheme extends MappedString(this, 255)
  object mOtherBankRoutingAddress extends MappedString(this, 255)
  object mOtherBranchRoutingScheme extends MappedString(this, 255)
  object mOtherBranchRoutingAddress extends MappedString(this, 255)
  object mIsBeneficiary extends MappedBoolean(this)
  object mDescription extends MappedString(this, 36)
  object mOtherAccountSecondaryRoutingScheme extends MappedString(this, 255)
  object mOtherAccountSecondaryRoutingAddress extends MappedString(this, 255)
  object mBespoke extends MappedOneToMany(MappedCounterpartyBespoke, MappedCounterpartyBespoke.mCounterparty, OrderBy(MappedCounterpartyBespoke.id, Ascending))

  override def createdByUserId = mCreatedByUserId.get
  override def name = mName.get
  override def thisBankId = mThisBankId.get
  override def thisAccountId = mThisAccountId.get
  override def thisViewId = mThisViewId.get
  override def counterpartyId = mCounterPartyId.get
  override def otherAccountRoutingScheme = mOtherAccountRoutingScheme.get
  override def otherAccountRoutingAddress: String  = mOtherAccountRoutingAddress.get
  override def otherBankRoutingScheme: String = mOtherBankRoutingScheme.get
  override def otherBranchRoutingScheme: String = mOtherBranchRoutingScheme.get
  override def otherBranchRoutingAddress: String = mOtherBranchRoutingAddress.get
  override def otherBankRoutingAddress: String = mOtherBankRoutingAddress.get
  override def isBeneficiary: Boolean = mIsBeneficiary.get
  override def description: String = mDescription.get
  override def otherAccountSecondaryRoutingScheme: String = mOtherAccountSecondaryRoutingScheme.get
  override def otherAccountSecondaryRoutingAddress: String = mOtherAccountSecondaryRoutingAddress.get
  override def bespoke: List[PostCounterpartyBespoke] = mBespoke.map(a=>PostCounterpartyBespoke(a.mKey.get,a.mVaule.get)).toList
}

object MappedCounterparty extends MappedCounterparty with LongKeyedMetaMapper[MappedCounterparty] {
  override def dbIndexes = UniqueIndex(mCounterPartyId) :: UniqueIndex(mName, mThisBankId, mThisAccountId, mThisViewId) :: super.dbIndexes
}

class MappedCounterpartyBespoke extends LongKeyedMapper[MappedCounterpartyBespoke] with IdPK {
  def getSingleton = MappedCounterpartyBespoke
  
  object mCounterparty extends MappedLongForeignKey(this, MappedCounterparty)
  object mKey extends MappedString(this, 255)
  object mVaule extends MappedString(this, 255)
}
object MappedCounterpartyBespoke extends MappedCounterpartyBespoke with LongKeyedMetaMapper[MappedCounterpartyBespoke]{}