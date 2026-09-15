package bench

import scala.jdk.CollectionConverters._

/**
 * Sanity check for Scala3PcCompletionBench: confirms the real
 * MtagsResolver/Embedded-resolved Scala 3 PresentationCompiler (loaded from
 * the locally-published dotty snapshot) returns genuine, non-zero
 * completions through the same PresentationCompiler.complete API used for
 * Scala 2.
 *
 * Run with: bench/runMain bench.DebugScala3Pc
 */
object DebugScala3Pc {
  def main(args: Array[String]): Unit = {
    val b = new Scala3PcCompletionBench()
    b.setup()
    val result = b.complete()
    println(s"completion count: ${result.getItems.size()}")
    result.getItems.asScala.take(20).foreach(item => println(s"  ${item.getLabel}"))
    b.tearDown()
  }
}
