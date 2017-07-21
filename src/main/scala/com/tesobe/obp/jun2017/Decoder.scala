package com.tesobe.obp.jun2017

import java.util.Date

import com.tesobe.obp.{Config, Util}
import io.circe.Error
import io.circe.generic.auto._
import io.circe.parser.decode


/**
  * Responsible for processing requests based on local example_import_jun2017.json file.
  *
  */
trait Decoder extends MappedDecoder with Config{

  def getBanks(getBanks: GetBanks) = {
    decodeLocalFile match {
      case Left(_) => Banks(getBanks.authInfo, List.empty[InboundBank])
      case Right(x) => Banks(getBanks.authInfo, x.banks.map(mapBankToInboundBank))
    }
  }

  def getBank(getBank: GetBank) = {
    decodeLocalFile match {
      case Left(_) => BankWrapper(getBank.authInfo, None)
      case Right(x) =>
        x.banks.filter(_.id == Some(getBank.bankId)).headOption match {
          case Some(x) => BankWrapper(getBank.authInfo, Some(mapBankToInboundBank(x)))
          case None => BankWrapper(getBank.authInfo, None)
        }
    }
  }

  def getUser(getUserbyUsernamePassword: GetUserByUsernamePassword) = {
    decodeLocalFile match {
      case Left(_) => UserWrapper(None)
      case Right(x) =>
        val userName = Some(getUserbyUsernamePassword.authInfo.username)
        val userPassword = Some(getUserbyUsernamePassword.password)
        x.users.filter(user => user.displayName == userName && user.password == userPassword).headOption match {
          case Some(x) => UserWrapper(Some(mapUserToInboundValidatedUser(x)))
          case None => UserWrapper(None)
        }
    }
  }
  
  def getAccounts(updateUserAccountViews: UpdateUserAccountViews) = {
    decodeLocalFile match {
      case Left(_) => OutboundUserAccountViewsBaseWapper(List.empty[InboundAccountJun2017])
      case Right(x) =>
        val userName = updateUserAccountViews.authInfo.username
        x.accounts.filter(account => account.owners.head == userName).headOption match {
          case Some(x) => OutboundUserAccountViewsBaseWapper(List(mapAdapterAccountToInboundAccountJun2017(x)))
          case None => OutboundUserAccountViewsBaseWapper(List.empty[InboundAccountJun2017])
        }
    }
  }
  
  def getAdapter(getAdapterInfo: GetAdapterInfo) = {
    AdapterInfo(data = Some(InboundAdapterInfo("", "OBP-Scala-South", "Jun2017", Util.gitCommit, (new Date()).toString)))
  }

  def getBankAccounts(getAccounts: GetAccounts): InboundBankAccounts
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


//object Decoder extends Decoder
