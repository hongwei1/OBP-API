package code.connector

import code.bankconnectors.generator.ConnectorBuilderUtil._
import net.liftweb.util.StringHelpers
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks

class StoredProcedureConnectorBuilderTest extends FeatureSpec with GivenWhenThen with Matchers with TableDrivenPropertyChecks {

  feature("ConnectorBuilderUtil commonMethodNames validation") {

    scenario("All methods in commonMethodNames should be valid Connector trait methods") {
      Given("the dynamically computed commonMethodNames")
      val validConnectorMethodNames = connectorDeclsMethodsReturnOBPRequiredType.map(_.name.toString).toSet

      Then("every method in commonMethodNames should exist in the Connector trait")
      val invalidMethods = commonMethodNames.filterNot(validConnectorMethodNames.contains)
      invalidMethods shouldBe empty
    }

    scenario("commonMethodNames should contain no duplicates") {
      Given("the dynamically computed commonMethodNames")
      Then("the list size should equal the distinct list size")
      commonMethodNames.size shouldEqual commonMethodNames.distinct.size
    }

    scenario("Every method in commonMethodNames should have OutBound and InBound DTOs") {
      Given("the dynamically computed commonMethodNames")
      Then("each method should have corresponding OutBound and InBound DTO classes")
      val missingDTOs = commonMethodNames.filterNot { methodName =>
        try {
          Class.forName(s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}")
          Class.forName(s"com.openbankproject.commons.dto.InBound${methodName.capitalize}")
          true
        } catch {
          case _: ClassNotFoundException => false
        }
      }
      missingDTOs shouldBe empty
    }

    scenario("All commonMethodNames should produce valid snakified stored procedure names") {
      Given("the dynamically computed commonMethodNames")
      Then("each method name should produce a non-empty snakified name")
      commonMethodNames.foreach { methodName =>
        val snakified = StringHelpers.snakify(methodName)
        snakified should not be empty
        snakified should fullyMatch regex """[a-z][a-z0-9_]*"""
      }
    }

    scenario("commonMethodNames and excludeMethods should not overlap") {
      Given("the dynamically computed commonMethodNames and excludeMethods")
      val commonSet = commonMethodNames.toSet
      val excludeSet = excludeMethods.toSet

      When("we compute the intersection")
      val overlap = commonSet.intersect(excludeSet)

      Then("the intersection should be empty")
      overlap shouldBe empty
    }
  }

  // Feature: dynamic-connector-method-generation, Property 1: Set relationship invariant
  // Feature: dynamic-connector-method-generation, Property 2: No Legacy methods
  // Feature: dynamic-connector-method-generation, Property 3: DTO existence guarantee
  // Feature: dynamic-connector-method-generation, Property 4: No duplicates
  feature("Dynamic commonMethodNames property tests") {

    val methodNameTable = Table("methodName", commonMethodNames: _*)
    val validConnectorMethodNames = connectorDeclsMethodsReturnOBPRequiredType.map(_.name.toString).toSet
    val excludeSet = excludeMethods.toSet

    // Feature: dynamic-connector-method-generation, Property 1: Set relationship invariant
    // **Validates: Requirements 1.1, 1.4, 2.4, 5.1, 5.2**
    scenario("Property 1: every commonMethodName is a valid Connector method and not in excludeMethods") {
      forAll(methodNameTable) { methodName =>
        validConnectorMethodNames should contain(methodName)
        excludeSet should not contain methodName
      }
    }

    // Feature: dynamic-connector-method-generation, Property 2: No Legacy methods
    // **Validates: Requirements 1.3, 5.4**
    scenario("Property 2: no commonMethodName ends with Legacy") {
      forAll(methodNameTable) { methodName =>
        methodName should not endWith "Legacy"
      }
    }

    // Feature: dynamic-connector-method-generation, Property 3: DTO existence guarantee
    // **Validates: Requirements 3.1, 3.2, 5.3**
    scenario("Property 3: every commonMethodName has OutBound and InBound DTOs") {
      forAll(methodNameTable) { methodName =>
        noException should be thrownBy Class.forName(s"com.openbankproject.commons.dto.OutBound${methodName.capitalize}")
        noException should be thrownBy Class.forName(s"com.openbankproject.commons.dto.InBound${methodName.capitalize}")
      }
    }

    // Feature: dynamic-connector-method-generation, Property 4: No duplicates
    // **Validates: Requirements 4.2**
    scenario("Property 4: commonMethodNames contains no duplicates") {
      commonMethodNames.size shouldEqual commonMethodNames.distinct.size
    }
  }

  // Requirements: 4.1, 5.5
  feature("Backward compatibility") {

    scenario("Dynamic commonMethodNames should contain all previously hardcoded methods (minus excludeMethods and non-OBP-type methods)") {
      Given("the original hardcoded method names snapshot")
      val originalHardcodedMethods = List(
        "getAdapterInfo", "getChallengeThreshold", "getChargeLevel", "getChargeLevelC2",
        "createChallenge", "getBank", "getBanks", "getBankAccountsForUser",
        "getBankAccountsBalances", "getBankAccountBalances", "getCoreBankAccounts",
        "getBankAccountsHeld", "getCounterpartyTrait", "getCounterpartyByCounterpartyId",
        "getCounterpartyByIban", "getCounterparties", "getTransactions", "getTransactionsCore",
        "getTransaction", "getPhysicalCardForBank", "deletePhysicalCardForBank",
        "getPhysicalCardsForBank", "createPhysicalCard", "updatePhysicalCard",
        "makePaymentv210", "makePaymentV400", "cancelPaymentV400",
        "createTransactionRequestv210", "getTransactionRequests210",
        "getTransactionRequestImpl", "createTransactionAfterChallengeV210",
        "updateBankAccount", "createBankAccount", "getBranch", "getBranches",
        "getAtm", "getAtms", "createTransactionAfterChallengev300", "makePaymentv300",
        "createTransactionRequestv300", "createCounterparty",
        "checkCustomerNumberAvailable", "createCustomer", "updateCustomerScaData",
        "updateCustomerCreditData", "updateCustomerGeneralData", "getCustomersByUserId",
        "getCustomerByCustomerId", "getCustomerByCustomerNumber", "getCustomerAddress",
        "createCustomerAddress", "updateCustomerAddress", "deleteCustomerAddress",
        "createTaxResidence", "getTaxResidence", "deleteTaxResidence", "getCustomers",
        "getCheckbookOrders", "getStatusOfCreditCardOrder", "createUserAuthContext",
        "createUserAuthContextUpdate", "deleteUserAuthContexts",
        "deleteUserAuthContextById", "getUserAuthContexts",
        "createOrUpdateProductAttribute", "getProduct", "getProducts",
        "getProductAttributeById", "getProductAttributesByBankAndCode",
        "deleteProductAttribute", "getAccountAttributeById",
        "createOrUpdateAccountAttribute", "createAccountAttributes",
        "getAccountAttributesByAccount", "createOrUpdateCardAttribute",
        "getCardAttributeById", "getCardAttributesFromProvider",
        "createAccountApplication", "getAllAccountApplication",
        "getAccountApplicationById", "updateAccountApplicationStatus",
        "getOrCreateProductCollection", "getProductCollection",
        "getOrCreateProductCollectionItem", "getProductCollectionItem",
        "getProductCollectionItemsTree", "createMeeting", "getMeetings", "getMeeting",
        "createOrUpdateKycCheck", "createOrUpdateKycDocument", "createOrUpdateKycMedia",
        "createOrUpdateKycStatus", "getKycChecks", "getKycDocuments", "getKycMedias",
        "getKycStatuses", "createMessage", "makeHistoricalPayment",
        "validateChallengeAnswer", "getBankAccountByIban", "getBankAccountByRouting",
        "getBankAccounts", "checkBankAccountExists", "createChallenges",
        "createTransactionRequestv400",
        "createTransactionRequestSepaCreditTransfersBGV1",
        "createTransactionRequestPeriodicSepaCreditTransfersBGV1",
        "getCustomersByCustomerPhoneNumber", "getTransactionAttributeById",
        "createOrUpdateCustomerAttribute", "createOrUpdateTransactionAttribute",
        "getCustomerAttributes", "getCustomerIdsByAttributeNameValues",
        "getCustomerAttributesForCustomers", "getTransactionIdsByAttributeNameValues",
        "getTransactionAttributes", "getBankAttributesByBank",
        "getCustomerAttributeById", "createDirectDebit", "deleteCustomerAttribute",
        "getPhysicalCardsForUser", "getChallengesByBasketId", "createChallengesC2",
        "createChallengesC3", "getChallenge", "getChallengesByTransactionRequestId",
        "getChallengesByConsentId", "validateAndCheckIbanNumber",
        "validateChallengeAnswerC2", "validateChallengeAnswerC3",
        "validateChallengeAnswerC4", "validateChallengeAnswerC5",
        "validateChallengeAnswerV2", "getCounterpartyByIbanAndBankAccountId",
        "getChargeValue", "saveTransactionRequestTransaction",
        "saveTransactionRequestChallenge", "getTransactionRequestTypes",
        "updateAccountLabel", "saveTransactionRequestStatusImpl",
        "getTransactionRequestTypeCharges", "getAccountsHeld", "getAccountsHeldByUser",
        "getRegulatedEntities", "getRegulatedEntityByEntityId",
        "getBankAccountBalancesByAccountId", "getBankAccountsBalancesByAccountIds",
        "getBankAccountBalanceById", "createOrUpdateBankAccountBalance",
        "deleteBankAccountBalance", "checkExternalUserCredentials",
        "checkExternalUserExists"
      )

      val excludeSet = excludeMethods.toSet
      val obpMethodNames = connectorDeclsMethodsReturnOBPRequiredType.map(_.name.toString).toSet
      // Filter: must not be excluded AND must be a valid OBP-type method (some original hardcoded
      // methods like createChallenge return String, which doesn't match the OBP return type pattern)
      val expectedMethods = originalHardcodedMethods
        .filterNot(excludeSet.contains)
        .filter(obpMethodNames.contains)
      val dynamicSet = commonMethodNames.toSet

      Then("the dynamic list should contain all expected methods")
      val missingMethods = expectedMethods.filterNot(dynamicSet.contains)
      withClue(s"Methods missing from dynamic commonMethodNames: ${missingMethods.mkString(", ")}") {
        missingMethods shouldBe empty
      }
    }
  }
}
