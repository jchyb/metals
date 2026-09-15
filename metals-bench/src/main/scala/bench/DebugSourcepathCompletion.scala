package bench

import scala.jdk.CollectionConverters._

/**
 * Sanity check for Scala2SourcepathCompletionBench / Scala3PcSourcepathCompletionBench:
 * confirms member completion on a sourcepath-only (never separately compiled)
 * target class returns the real method0/method1/method2 candidates from
 * SymbolResolutionCorpus, proving the sourcepath supplier actually wired up.
 *
 * Run with: bench/runMain bench.DebugSourcepathCompletion
 */
object DebugSourcepathCompletion {
  def main(args: Array[String]): Unit = {
    println("--- scala2 ---")
    val b2 = new Scala2SourcepathCompletionBench()
    b2.setup()
    val r2 = b2.complete()
    println(s"completion count: ${r2.getItems.size()}")
    r2.getItems.asScala.take(20).foreach(item => println(s"  ${item.getLabel}"))
    b2.tearDown()

    println("--- scala3 ---")
    val b3 = new Scala3PcSourcepathCompletionBench()
    b3.setup()
    val r3 = b3.complete()
    println(s"completion count: ${r3.getItems.size()}")
    r3.getItems.asScala.take(20).foreach(item => println(s"  ${item.getLabel}"))
    b3.tearDown()
  }
}
