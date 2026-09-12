package code.api.util

import org.json4s.JValue

/**
 * A JValue carried as a case class so it can stand in for a ResourceDoc example body.
 *
 * Nothing about it is Berlin Group specific, but it used to be declared in
 * JSONFactory_BERLIN_GROUP_1_3.scala, which made twenty UK Open Banking v3.1.0 handlers,
 * JSONFactory1_4_0 and the resource-doc aggregation all depend on the Berlin Group package
 * for this one wrapper — and meant UK Open Banking could not be extracted without Berlin
 * Group underneath it. It lives here so both standards depend on the core instead of on
 * each other.
 *
 * ResourceDocsAPIMethods strips this wrapper back out when it serialises a doc; see the note
 * at ResourceDocsAPIMethods.scala:1251.
 */
case class JvalueCaseClass(jvalueToCaseclass: JValue)
