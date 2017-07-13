package com.tesobe.obp.jun2017

import com.tesobe.obp.GetBankAccounts.getBasicBankAccountsForUser
import com.tesobe.obp.Nt1cMf.getBalance
import com.tesobe.obp.GetBankAccounts.hexEncodedSha256
import scala.collection.mutable.{ListBuffer,Map}


/**
  * Responsible for processing requests based on local example_import_jun2017.json file.
  *
  */
object LeumiDecoder extends Decoder {
  
  var mapAccountIdToAccountNr = Map[String, String]()
  var mapAccountNrToAccountId= Map[String, String]()

  def getOrCreateAccountId(accountNr: String): String = {
    if (mapAccountNrToAccountId.contains(accountNr)) { mapAccountNrToAccountId(accountNr) }
    else {
      val accountId = hexEncodedSha256(accountNr + "fjdsaFDSAefwfsalfid")
      mapAccountIdToAccountNr += (accountId -> accountNr)
      mapAccountNrToAccountId += (accountNr -> accountId)
      accountId
    }
  }

  override def getBankAccounts(getAccounts: GetAccounts) = {
    // userid is path to test json file
    val userid = "./src/test/resources/joni_result.json"
    val mfAccounts = getBasicBankAccountsForUser(userid)
    var result = new ListBuffer[InboundAccountJune2017]()
    for (i <- mfAccounts) {
      //TODO: This is by choice and needs verification
      //Create OwnerRights and accountViewer for result InboundAccount2017 creation
      val hasOwnerRights: Boolean = i.accountPermissions.canMakeExternalPayments || i.accountPermissions.canMakeInternalPayments
      val hasViewerRights: Boolean = i.accountPermissions.canSee
      val  viewsToGenerate  = {
        if (hasOwnerRights) {
          List("Owner")
        }
        else if (hasViewerRights) {
          List("Accountant")
        } else {
          List("")
        }
      }
      //Create Owner for result InboundAccount2017 creation
      val accountOwner = if (hasOwnerRights) {List(userid)} else {List("")}
             
      result += InboundAccountJune2017(
          "",//errorCode: String,
          "10",//bankId: String,
          i.branchNr,//branchId: String,
          getOrCreateAccountId(i.accountNr),//accountId: String,
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
  

}



