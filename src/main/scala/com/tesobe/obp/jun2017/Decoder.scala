package com.tesobe.obp.jun2017

import java.util.Date

import com.tesobe.obp.GetBankAccounts.getBasicBankAccountsForUser
import com.tesobe.obp.Nt1cMf.getBalance
import com.tesobe.obp.Util
import io.circe.Error
import io.circe.generic.auto._
import io.circe.parser.decode

import scala.collection.mutable.ListBuffer


/**
  * Responsible for processing requests based on local example_import_jun2017.json file.
  *
  */
trait Decoder extends MappedDecoder {

  def getBanks(getBanks: GetBanks) = {
    decodeLocalFile match {
      case Left(_) => Banks(getBanks.authInfo, List.empty[InboundBank])
      case Right(x) => Banks(getBanks.authInfo, x.banks.map(mapBankN))
    }
  }

  def getBank(getBank: GetBank) = {
    decodeLocalFile match {
      case Left(_) => BankWrapper(getBank.authInfo, None)
      case Right(x) =>
        x.banks.filter(_.id == Some(getBank.bankId)).headOption match {
          case Some(x) => BankWrapper(getBank.authInfo, Some(mapBankN(x)))
          case None => BankWrapper(getBank.authInfo, None)
        }
    }
  }

  def getUser(getUserbyUsernamePassword: GetUserByUsernamePassword) = {
    decodeLocalFile match {
      case Left(_) => UserWrapper(None)
      case Right(x) =>
        val userName = Some(getUserbyUsernamePassword.username)
        val userPassword = Some(getUserbyUsernamePassword.password)
        x.users.filter(user => user.displayName == userName && user.password == userPassword).headOption match {
          case Some(x) => UserWrapper(Some(mapUserN(x)))
          case None => UserWrapper(None)
        }
    }
  }
  
  def getAccounts(updateUserAccountViews: UpdateUserAccountViews) = {
    decodeLocalFile match {
      case Left(_) => OutboundUserAccountViewsBaseWapper(List.empty[InboundAccountJune2017])
      case Right(x) =>
        val userName = updateUserAccountViews.username
        x.accounts.filter(account => account.owners.head == userName).headOption match {
          case Some(x) => OutboundUserAccountViewsBaseWapper(List(mapAccountN(x)))
          case None => OutboundUserAccountViewsBaseWapper(List.empty[InboundAccountJune2017])
        }
    }
  }
  
  def getAdapter(getAdapterInfo: GetAdapterInfo) = {
    AdapterInfo(data = Some(InboundAdapterInfo("", "OBP-Scala-South", "June2017", Util.gitCommit, (new Date()).toString)))
  }

  def getBankAccounts(getAccounts: GetAccounts) = {
    // userid is path to test json file
    val userid = "./src/test/resources/joni_result.json"
    val mfAccounts = getBasicBankAccountsForUser(userid)
    var result = new ListBuffer[InboundAccountJune2017]()
    for (i <- mfAccounts) {
      //TODO: This is by choice and needs verification
      val accountOwnerRights = i.accountPermissions.externalTransactions || i.accountPermissions.internalTransactions
      val accountViewerRights = i.accountPermissions.canSee
      val  viewsToGenerate  = {
        if (accountOwnerRights) {
          List("Owner")
        }
        else if (accountViewerRights) {
          List("Accountant")
        } else {
          List("")
        }
      }
      val accountOwner = if (accountOwnerRights) {List(userid)} else {List("")}
      
       
      result += InboundAccountJune2017(
          "",//errorCode: String,
          "10",//bankId: String,
          i.branchNr,//branchId: String,
          i.accountNr,//accountId: String,
          "",//number: String,
          i.accountType,//accountType: String,
          getBalance("./src/test/resources/nt1c_result.json"),//balanceAmount: String,
          "ILS",//balanceCurrency: String,
          accountOwner,//owners: List[String],
          viewsToGenerate,//generateViews: List[String],
          "",//bankRoutingScheme: String,
          "",//bankRoutingAddress: String,
          "",//branchRoutingScheme: String,
          "", //branchRoutingAddress: String,
          "", //accountRoutingScheme: String,
          "" //accountRoutingAddress: String
        )
      }
    BankAccounts(getAccounts.authInfo, result.toList)
  }
  /*
   * Decodes example_import_jun2017.json file to com.tesobe.obp.jun2017.Example
   */
  private val decodeLocalFile: Either[Error, Example] = {
    val resource = scala.io.Source.fromResource("example_import_jun2017.json")
    val lines = resource.getLines()
    val json = lines.mkString
    decode[com.tesobe.obp.jun2017.Example](json)
  }

}


object Decoder extends Decoder
