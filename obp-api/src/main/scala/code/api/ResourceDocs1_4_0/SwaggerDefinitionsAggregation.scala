package code.api.ResourceDocs1_4_0

/**
 * The example values that feed swagger definition generation, per document.
 *
 * SwaggerJSONFactory.loadDefinitions reflects over these to emit one definition per type, so each
 * list has to be complete: a source left out disappears from the generated document with no error.
 *
 * The two documents deliberately differ, and did before this object existed — the connector
 * message-docs swagger also needs MessageDocsSwaggerDefinitions, the resource-docs swagger does
 * not. Merging them would quietly add definitions to one of the published documents, so they stay
 * separate here and each keeps exactly the sources it had.
 *
 * Having the aggregation in this package rather than in code.api.util is also what lets the UK
 * Open Banking examples live with their own types: naming a standard is this package's job —
 * SwaggerJSONFactory already names all three UK Open Banking versions — while code.api.util must
 * not, and CoreDependencyDirectionTest enforces that.
 */
object SwaggerDefinitionsAggregation {

  /** For GET /resource-docs/.../swagger. */
  val forResourceDocs: Seq[AnyRef] =
    SwaggerDefinitionsJSON.allFields ++
      code.api.UKOpenBanking.SwaggerDefinitionsUKOB.allFields

  /** For the connector message-docs swagger, which additionally documents the message types. */
  val forMessageDocs: Seq[AnyRef] =
    MessageDocsSwaggerDefinitions.allFields ++ forResourceDocs
}
