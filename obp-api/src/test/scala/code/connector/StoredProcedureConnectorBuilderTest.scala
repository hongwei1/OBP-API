package code.connector

import code.bankconnectors.generator.ConnectorBuilderUtil._
import net.liftweb.util.StringHelpers
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

class StoredProcedureConnectorBuilderTest extends FeatureSpec with GivenWhenThen with Matchers {

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
}
