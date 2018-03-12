package code.views

import code.api.util.ErrorMessages.ViewIdNotSupported
import code.model._
import code.model.dataAccess.{MappedAccountView, ViewImpl, ViewPrivileges}
import code.setup.{DefaultUsers, ServerSetup}
import net.liftweb.common.Failure
import net.liftweb.mapper.By

class MappedViewsTest extends ServerSetup with DefaultUsers{
  
  override def beforeAll() = {
    super.beforeAll()
    ViewImpl.bulkDelete_!!()
    MappedAccountView.bulkDelete_!!()
  }
  
  override def afterEach() = {
    super.afterEach()
    ViewImpl.bulkDelete_!!()
    MappedAccountView.bulkDelete_!!()
  }
  
  val bankIdAccountId = BankIdAccountId(BankId("1"),AccountId("2"))
  
  val viewIdOwner = "Owner"
  val viewIdPublic = "Public"
  val viewIdAccountant = "Accountant"
  val viewIdAuditor = "Auditor"
  val viewIdNotSupport = "NotSupport"
  
  
  feature("test some important methods in MappedViews ") {
    
    scenario("test - getOrCreateAccountView") {
      
      Given("set up four normal Views")
      var viewOwner = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdOwner)
      var viewPublic = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdPublic)
      var viewAccountant = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdAccountant)
      var viewAuditor = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdAuditor)
      var allExistingViewsForOneAccount = MapperViews.viewsForAccount(bankIdAccountId)
      
      Then("Check the result from database. it should have 4 views and with the right viewId")
      viewOwner.head.viewId.value should equal("Owner".toLowerCase())
      viewPublic.head.viewId.value should equal("public".toLowerCase())
      viewAccountant.head.viewId.value should equal("Accountant".toLowerCase())
      viewAuditor.head.viewId.value should equal("Auditor".toLowerCase())
      allExistingViewsForOneAccount.length should equal(4)
      
      Then("We set the four normal views again")
      viewOwner = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdOwner)
      viewPublic = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdPublic)
      viewAccountant = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdAccountant)
      viewAuditor = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdAuditor)
      allExistingViewsForOneAccount = MapperViews.viewsForAccount(bankIdAccountId)
  
      Then("Check the result from database again. it should have four views and with the right viewId, there should be not changed.")
      viewOwner.head.viewId.value should equal("Owner".toLowerCase())
      viewPublic.head.viewId.value should equal("public".toLowerCase())
      viewAccountant.head.viewId.value should equal("Accountant".toLowerCase())
      viewAuditor.head.viewId.value should equal("Auditor".toLowerCase())
      allExistingViewsForOneAccount.length should equal(4)
  
  
      Then("set up four wrong View name, do not support this viewId")
      val wrongViewId = "WrongViewId"
      val wrongView = MapperViews.getOrCreateAccountView(bankIdAccountId, wrongViewId)
  
      wrongView should equal(Failure(ViewIdNotSupported+ s"Your input viewId is :$wrongViewId"))
  
    }
  
    scenario("test - getOrCreateViewPrivilege") {
  
      Given("the view and user for this method")
      val viewOwner = MapperViews.getOrCreateAccountView(bankIdAccountId, viewIdOwner).head
      viewOwner.viewId.value should equal("Owner".toLowerCase())
      
      Then("call the method, create the Privilege")
      MapperViews.getOrCreateViewPrivilege(viewOwner, resourceUser1)
      
      Then("Check the result.")
      val accountView = viewOwner.mappedAccountView
      val numberOfViewPrivilege= ViewPrivileges.count(
        By(ViewPrivileges.user, resourceUser1.resourceUserId.value), 
        By(ViewPrivileges.view, accountView.id.get)
      )
      numberOfViewPrivilege should be(1)
      
      Then("call the method again")
      MapperViews.getOrCreateViewPrivilege(viewOwner, resourceUser1)
      
      Then("We check the result, the number should be the same")
      val numberOfViewPrivilege2= ViewPrivileges.count(
        By(ViewPrivileges.user, resourceUser1.resourceUserId.value),
        By(ViewPrivileges.view, accountView.id.get)
      )
      numberOfViewPrivilege2 should be(1)
    }
  
  }
  
  
}
