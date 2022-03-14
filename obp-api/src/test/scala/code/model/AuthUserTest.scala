package code.model

import code.UserRefreshes.MappedUserRefreshes
import code.accountholders.MapperAccountHolders
import code.bankconnectors.Connector
import code.connector.MockedCbsConnector
import code.model.dataAccess.{AuthUser, ViewImpl, ViewPrivileges}
import code.setup.{DefaultUsers, PropsReset, ServerSetup}
import code.views.MapperViews
import code.views.system.{AccountAccess, ViewDefinition}
import com.openbankproject.commons.model.{CreditLimit, CreditRating, CustomerCommons, CustomerFaceImage, InboundAccount, InboundAccountCommons}
import net.liftweb.mapper.{By, PreCache}

import java.util.Date
import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by zhanghongwei on 17/07/2017.
  */
class AuthUserTest extends ServerSetup with DefaultUsers with PropsReset{
  
  
  override def beforeAll() = {
    super.beforeAll()
    Connector.connector.default.set(MockedCbsConnector)
    ViewDefinition.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
    AccountAccess.bulkDelete_!!()
    MappedUserRefreshes.bulkDelete_!!()
  }
  
  override def afterEach() = {
    super.afterEach()
    Connector.connector.default.set(Connector.buildOne)
    ViewDefinition.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
    AccountAccess.bulkDelete_!!()
    MappedUserRefreshes.bulkDelete_!!()
  }
  
  val bankIdAccountId1 = MockedCbsConnector.bankIdAccountId
  val bankIdAccountId2 = MockedCbsConnector.bankIdAccountId2

  def account1Access = AccountAccess.findAll(
    By(AccountAccess.user_fk, resourceUser1.userPrimaryKey.value),
    By(AccountAccess.bank_id, bankIdAccountId1.bankId.value),
    By(AccountAccess.account_id, bankIdAccountId1.accountId.value),
  )
  
  def account2Access = AccountAccess.findAll(
    By(AccountAccess.user_fk, resourceUser1.userPrimaryKey.value),
    By(AccountAccess.bank_id, bankIdAccountId2.bankId.value),
    By(AccountAccess.account_id, bankIdAccountId2.accountId.value),
  )
  
  def account1AccessUser2 = AccountAccess.findAll(
    By(AccountAccess.user_fk, resourceUser2.userPrimaryKey.value),
    By(AccountAccess.bank_id, bankIdAccountId1.bankId.value),
    By(AccountAccess.account_id, bankIdAccountId1.accountId.value),
  )
  
  def account2AccessUser2 = AccountAccess.findAll(
    By(AccountAccess.user_fk, resourceUser2.userPrimaryKey.value),
    By(AccountAccess.bank_id, bankIdAccountId2.bankId.value),
    By(AccountAccess.account_id, bankIdAccountId2.accountId.value),
  )

  def accountholder1 = MapperAccountHolders.getAccountHolders(bankIdAccountId1.bankId, bankIdAccountId1.accountId)
  def accountholder2 = MapperAccountHolders.getAccountHolders(bankIdAccountId2.bankId, bankIdAccountId2.accountId)
  
  def user1CustomerLinks = code.usercustomerlinks.MappedUserCustomerLinkProvider.getUserCustomerLinksByUserId(resourceUser1.userId)
  def user2CustomerLinks = code.usercustomerlinks.MappedUserCustomerLinkProvider.getUserCustomerLinksByUserId(resourceUser2.userId)

  def allViewsForAccount1 = MapperViews.availableViewsForAccount(bankIdAccountId1)
  def allViewsForAccount2 = MapperViews.availableViewsForAccount(bankIdAccountId2)
  
  def mappedUserRefreshesLength= MappedUserRefreshes.findAll().length


  val accountsHeldEmpty = List()
  
  val account1Held = List(
    InboundAccountCommons(
      bankId = bankIdAccountId1.bankId.value,
      accountId = bankIdAccountId1.accountId.value,
      viewsToGenerate = "Owner" :: Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    )
  )
  
  val account2Held = List(
    InboundAccountCommons(
      bankId = bankIdAccountId2.bankId.value,
      accountId = bankIdAccountId2.accountId.value,
      viewsToGenerate = "Owner" :: Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    )
  )
  
  val twoAccountsHeld = List(
    InboundAccountCommons(
      bankId = bankIdAccountId1.bankId.value,
      accountId = bankIdAccountId1.accountId.value,
      viewsToGenerate = "Owner" :: Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    ),
    InboundAccountCommons(
      bankId = bankIdAccountId2.bankId.value,
      accountId = bankIdAccountId2.accountId.value,
      viewsToGenerate = "Owner" :: Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    )
  )

  val customersEmpty = List()

  val customer1 = List(CustomerCommons(
      customerId="customre-id-1",
      bankId="string",
      number="string",
      legalName="string",
      mobileNumber="string",
      email="string",
      faceImage= CustomerFaceImage(date=new Date(),
        url="string"),
      dateOfBirth=new Date(),
      relationshipStatus="string",
      dependents=123,
      dobOfDependents=List(new Date()),
      highestEducationAttained="string",
      employmentStatus="string",
      creditRating= CreditRating(rating="string",
        source="string"),
      creditLimit= CreditLimit(currency="string",
        amount="string"),
      kycStatus=true,
      lastOkDate=new Date(),
      title="string",
      branchId="string",
      nameSuffix="string"
  ))

  val customer2 = List(
    CustomerCommons(
      customerId="customre-id-2",
      bankId="string",
      number="string",
      legalName="string",
      mobileNumber="string",
      email="string",
      faceImage= CustomerFaceImage(date=new Date(),
        url="string"),
      dateOfBirth=new Date(),
      relationshipStatus="string",
      dependents=123,
      dobOfDependents=List(new Date()),
      highestEducationAttained="string",
      employmentStatus="string",
      creditRating= CreditRating(rating="string",
        source="string"),
      creditLimit= CreditLimit(currency="string",
        amount="string"),
      kycStatus=true,
      lastOkDate=new Date(),
      title="string",
      branchId="string",
      nameSuffix="string"
    )
  )

  val twoCustomers = List(
    CustomerCommons(
      customerId="customre-id-1",
      bankId="string",
      number="string",
      legalName="string",
      mobileNumber="string",
      email="string",
      faceImage= CustomerFaceImage(date=new Date(),
        url="string"),
      dateOfBirth=new Date(),
      relationshipStatus="string",
      dependents=123,
      dobOfDependents=List(new Date()),
      highestEducationAttained="string",
      employmentStatus="string",
      creditRating= CreditRating(rating="string",
        source="string"),
      creditLimit= CreditLimit(currency="string",
        amount="string"),
      kycStatus=true,
      lastOkDate=new Date(),
      title="string",
      branchId="string",
      nameSuffix="string"
    ),
    CustomerCommons(
      customerId="customre-id-2",
      bankId="string",
      number="string",
      legalName="string",
      mobileNumber="string",
      email="string",
      faceImage= CustomerFaceImage(date=new Date(),
        url="string"),
      dateOfBirth=new Date(),
      relationshipStatus="string",
      dependents=123,
      dobOfDependents=List(new Date()),
      highestEducationAttained="string",
      employmentStatus="string",
      creditRating= CreditRating(rating="string",
        source="string"),
      creditLimit= CreditLimit(currency="string",
        amount="string"),
      kycStatus=true,
      lastOkDate=new Date(),
      title="string",
      branchId="string",
      nameSuffix="string"
    )
  )


  feature("Test the refreshUser method") {
    scenario("we fake the output from getBankAccounts(), and check the functions there") {

      When("We call the method use resourceUser1")
      val result = Await.result(AuthUser.refreshUser(resourceUser1, None), Duration.Inf)

      Then("We check the accountHolders")
      var accountholder1 = MapperAccountHolders.getAccountHolders(bankIdAccountId1.bankId, bankIdAccountId1.accountId)
      var accountholder2 = MapperAccountHolders.getAccountHolders(bankIdAccountId2.bankId, bankIdAccountId2.accountId)
      var accountholders = MapperAccountHolders.findAll()
      accountholder1.head.userPrimaryKey should equal(resourceUser1.userPrimaryKey)
      accountholder2.head.userPrimaryKey should equal(resourceUser1.userPrimaryKey)
      accountholders.length should equal(2)

      Then("We check the views") 
      val allViewsForAccount1 = MapperViews.availableViewsForAccount(bankIdAccountId1)
      val allViewsForAccount2 = MapperViews.availableViewsForAccount(bankIdAccountId2)
      val allViews = ViewDefinition.findAll()
      allViewsForAccount1.toString().contains("owner") should equal(true)
      allViewsForAccount1.toString().contains("_public") should equal(true)
      allViewsForAccount1.toString().contains("accountant") should equal(true)
      allViewsForAccount1.toString().contains("auditor") should equal(true)
      allViewsForAccount2.toString().contains("owner") should equal(true)
      allViewsForAccount2.toString().contains("_public") should equal(true)
      allViewsForAccount2.toString().contains("accountant") should equal(true)
      allViewsForAccount2.toString().contains("auditor") should equal(true)
      allViews.length should equal(5) // 3 system views + 2 custom views

      Then("We check the AccountAccess")
      val numberOfAccountAccess = AccountAccess.findAll().length
      numberOfAccountAccess should equal(8) 

    }
  }
  
  feature("Test the refreshViewsAccountAccessAndHoldersAndUserCustomerLinks- accounts -- method") {
    scenario("Test one account views,account access and account holder") {
      
      When("1st Step: no accounts in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeldEmpty, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("There is not system views at all in the ViewDefinition table, so both should be Empty")
      allViewsForAccount1.map(_.viewId.value) should equal(List())
      allViewsForAccount2.map(_.viewId.value) should equal(List())

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (0)

      Then("2rd Step: there is 1st account in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, account1Held, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      Then("3rd: we remove the accounts ")
      val accountsHeld = List()
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeld, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

    }
    
    scenario("Test two accounts views,account access and account holder") {

      When("1rd Step: no accounts in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeldEmpty, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("There is not system views at all in the ViewDefinition table, so both should be Empty")
      allViewsForAccount1.map(_.viewId.value) should equal(List())
      allViewsForAccount2.map(_.viewId.value) should equal(List())

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (0)
      
      When("2rd block, we prepare one account")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, account1Held, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      Then("3rd:  we have two accounts in the accountsHeld")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, twoAccountsHeld, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(1)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(1)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)
        

      When("4th, we removed the 1rd account, only have 2rd account there.")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, account2Held, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(1)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(1)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)
      
      When("5th, we do not have any accounts ")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeldEmpty, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

    }

    scenario("Test two users, account views,account access and account holder") {

      When("1st Step: no accounts in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeldEmpty, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("There is not system views at all in the ViewDefinition table, so both should be Empty")
      allViewsForAccount1.map(_.viewId.value) should equal(List())
      allViewsForAccount2.map(_.viewId.value) should equal(List())

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (0)

      Then("2rd Step: 1st user and  1st account in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, account1Held, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)
      account1AccessUser2.length should equal(0)
      account2AccessUser2.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)


      Then("3rd Step: 2rd user and 1st account in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser2, account1Held, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(2)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)
      account1AccessUser2.length should equal(1)
      account2AccessUser2.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (2)

      When("4th, User1 we do not have any accounts ")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeldEmpty, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)
      account1AccessUser2.length should equal(1)
      account2AccessUser2.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (2)

    }
  }

  feature("Test the refreshViewsAccountAccessAndHoldersAndUserCustomerLinks --> accounts+customers -- method") {
    scenario("Test one account views,account access and account holder") {

      When("1st Step: no accounts in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeldEmpty, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)
      user1CustomerLinks.size should be(0)

      Then("There is not system views at all in the ViewDefinition table, so both should be Empty")
      allViewsForAccount1.map(_.viewId.value) should equal(List())
      allViewsForAccount2.map(_.viewId.value) should equal(List())

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (0)

      Then("We check the customer link")
      user1CustomerLinks.size should be(0)

      Then("2rd Step: there is 1st account + 1rd customer in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, account1Held, customer1)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      Then("We check the customer link")
      user1CustomerLinks.size should be(1)

      Then("3rd: we remove the accounts  and customers")
      val accountsHeld = List()
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, Nil, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the customer link")
      user1CustomerLinks.size should be(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

    }

    scenario("Test two accounts views,account access, account holder and customer links") {

      When("1rd Step: no accounts in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeldEmpty, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("There is not system views at all in the ViewDefinition table, so both should be Empty")
      allViewsForAccount1.map(_.viewId.value) should equal(List())
      allViewsForAccount2.map(_.viewId.value) should equal(List())

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (0)

      When("2rd block, we prepare one account and one customer")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, account1Held, customer1)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)

      Then("We check the customer link")
      user1CustomerLinks.size should be(1)
      user2CustomerLinks.size should be(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      Then("3rd:  we have two accounts in the accountsHeld")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, twoAccountsHeld, twoCustomers)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(1)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(1)

      Then("We check the customer link")
      user1CustomerLinks.size should be(2)
      
      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)


      When("4th, we removed the 1rd account, only have 2rd account there.")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, account2Held, customer2)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(1)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(1)

      Then("We check the customer link")
      user1CustomerLinks.size should be(1)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      When("5th, we do not have any accounts ")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeldEmpty, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the customer link")
      user1CustomerLinks.size should be(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

    }

    scenario("Test two users, account views,account access, account holder and customer links") {

      When("1st Step: no accounts or customers in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeldEmpty, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("There is not system views at all in the ViewDefinition table, so both should be Empty")
      allViewsForAccount1.map(_.viewId.value) should equal(List())
      allViewsForAccount2.map(_.viewId.value) should equal(List())

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (0)

      Then("We check the customer link")
      user1CustomerLinks.size should be(0)

      Then("2rd Step: 1st user and  1st account and 1st customer in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, account1Held, customer1)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)
      account1AccessUser2.length should equal(0)
      account2AccessUser2.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      Then("We check the customer link")
      user1CustomerLinks.size should be(1)
      user2CustomerLinks.size should be(0)
      
      Then("3rd Step: 2rd user and 1st account and 1st customer in the List")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser2, account1Held, customer1)

      Then("We check the accountHolders")
      accountholder1.size should be(2)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)
      account1AccessUser2.length should equal(1)
      account2AccessUser2.length should equal(0)
      
      Then("We check the customer link")
      user1CustomerLinks.size should be(1)
      user2CustomerLinks.size should be(1)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (2)

      When("4th, User1 we do not have any accounts or customers ")
      AuthUser.refreshViewsAccountAccessAndHoldersAndUserCustomerLinks(resourceUser1, accountsHeldEmpty, customersEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)
      account1AccessUser2.length should equal(1)
      account2AccessUser2.length should equal(0)

      Then("We check the customer link")
      user1CustomerLinks.size should be(0)
      user2CustomerLinks.size should be(1)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (2)
    }
  }
}
