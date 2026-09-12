package code.api.util

/** Reflection predicates with no application behind them. APIUtil delegates to these so there is
  * one definition rather than a copy on each side of the module boundary. */
object KernelReflection {
  /** True for a field name that is a real declared value rather than a synthetic or aggregate one:
    * `$`-containing names are compiler-generated, and `allFieldsAndValues` is the aggregate itself. */
  def notExstingBaseClass(input: String): Boolean =
    !input.contains("$") && !input.equalsIgnoreCase("allFieldsAndValues")
}
