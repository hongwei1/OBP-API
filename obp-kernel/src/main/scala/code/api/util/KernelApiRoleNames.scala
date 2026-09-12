package code.api.util

/**
 * Names of the few roles that error messages have to quote.
 *
 * ApiRole itself cannot live in this module: besides the static enumeration it also registers
 * roles created at runtime for dynamic entities and endpoints, which pulls in
 * DynamicEntityHelper and DynamicEndpointHelper — application code with persistence behind it.
 * So the kernel holds only the handful of names it needs to render a message.
 *
 * Copied constants drift, so they are not left to trust: ErrorMessagesRoleNameTest in obp-api,
 * which can see both, fails if either name stops matching the real ApiRole value.
 */
object KernelApiRoleNames {
  val CanCreateAnyTransactionRequest = "CanCreateAnyTransactionRequest"
  val CanCreateEntitlementAtAnyBank = "CanCreateEntitlementAtAnyBank"
}
