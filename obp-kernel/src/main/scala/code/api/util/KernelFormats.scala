package code.api.util

/**
 * Date format patterns the API contract is written in.
 *
 * The pattern strings live here rather than in APIUtil because they are part of what the API
 * *says* — error messages quote them, documentation quotes them — and that has to be available
 * to code with no application behind it. APIUtil still owns the SimpleDateFormat instances
 * (thread-local, zone-aware); it reads the patterns from here, so there is one spelling of each.
 */
object KernelFormats {
  /** UTC, milliseconds, Z suffix: 2025-01-01T01:01:01.000Z */
  val DateWithMs = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
}
