import net.liftweb.mapper._
class Thing extends LongKeyedMapper[Thing] with IdPK {
  def getSingleton: KeyedMetaMapper[Long, Thing] = Thing
}
object Thing extends Thing with LongKeyedMetaMapper[Thing]

/*
Minimal reproduction of the blocker found while flipping obp-api to Scala 3.

Compile with a Scala 3 compiler against lift-persistence_2.13 on the classpath:

  mvn -q -pl obp-api dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -DincludeScope=compile
  java -cp <scala3-compiler jars>:$(cat /tmp/cp.txt) dotty.tools.dotc.Main \
       -classpath "$(cat /tmp/cp.txt)" -d out docs/scala3/lift-mapper-scala3-repro.scala

Result on 3.3.8 AND on 3.7.2 (both tried):

  assertion failure for net.liftweb.mapper.Mapper[...] & OwnerType <:< net.liftweb.mapper.Mapper[...], frozen = true
  ...
  exception occurred while typechecking
  An unhandled exception was thrown in the compiler.

In the full build the same thing surfaces as "Cyclic reference" on the class and object
declarations, 12 of them, one pair per entity the compiler reaches before giving up.

What it is: Lift Mapper is built on an F-bounded self-type (Mapper[A] with OwnerType), and
`object X extends class X` - the idiom every Lift entity uses to pair an instance type with
its meta object - makes Scala 3's type comparer intersect Mapper[...] with OwnerType while
the class is still being completed.

What it is NOT (each ruled out by experiment, so nobody repeats them):
  - a compiler-version bug: identical on 3.3.8 (LTS) and 3.7.2 (latest)
  - the getSingleton type annotation: fails both with `: KeyedMetaMapper[Long, X]` and with
    the type inferred
  - the dbIndexes annotation that -Xsource:3 added: removing it in all 43 files changed nothing

Why it matters: obp-api defines ~140 Lift Mapper entities. They are the persistence layer, and
they must be Scala 3 because obp-api is. Consuming lift-persistence as a _2.13 library - the
plan's premise, and correct as far as it goes - does not help, because the code that crashes
the compiler is the *consumer*, not the library.

Options, none of them small:
  1. Patch the lift-persistence fork (OBP controls it) so the self-type structure no longer
     trips the comparer, and republish for _2.13.
  2. Keep the entity layer on Scala 2.13 in its own module, as obp-commons does - but entities
     reference obp-api types and services reference entities, so the split is not clean.
  3. Finish the Doobie migration for persistence before the flip. The plan explicitly recorded
     that Doobie was no longer a prerequisite; this finding reverses that.
  4. Report upstream as a dotty bug (a compiler crash is a bug whatever the input) and wait.
*/
