package code.api.util

import code.api.APIFailureNewStyle
import org.json4s.Extraction

/**
 * Renders an API failure as the JSON string the error paths log and return.
 *
 * Lived on ErrorMessages until that object moved to obp-kernel. It could not go with it: it needs
 * APIFailureNewStyle and CallContext, which are application types, and a json4s Formats in scope.
 * It was never an error *message* anyway — it is how a failure is serialised.
 */
object ApiFailureRenderer {
  private implicit val formats: org.json4s.Formats = CustomJsonFormats.formats

  def apiFailureToString(code: Int, message: String, context: Option[CallContext]): String =
    com.openbankproject.commons.util.JsonAliases.compactRender(
      Extraction.decompose(
        APIFailureNewStyle(failMsg = message, failCode = code, context.map(_.toLight))
      )
    )

  def apiFailureToString(code: Int, message: String, context: CallContext): String =
    apiFailureToString(code, message, Some(context))
}
