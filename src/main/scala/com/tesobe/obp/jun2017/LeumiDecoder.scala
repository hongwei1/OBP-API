package com.tesobe.obp.jun2017

import com.tesobe.obp.GetBankAccounts.getBasicBankAccountsForUser
import com.tesobe.obp.Nt1cMf.getBalance

import scala.collection.mutable.ListBuffer


/**
  * Responsible for processing requests based on local example_import_jun2017.json file.
  *
  */
object LeumiDecoder extends Decoder {



  override def getBankAccounts(getAccounts: GetAccounts) = {
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
  

}



