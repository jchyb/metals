package bench

import scala.jdk.CollectionConverters._

/**
 * Sanity check for the fixed Scala2CompletionBench: confirms the real
 * MetalsGlobal-backed PresentationCompiler.complete path returns genuine,
 * non-zero completions (unlike the old plain-Global version, which returned 0).
 *
 * Run with: bench/runMain bench.DebugCompletion3
 */
object DebugCompletion3 {
  def main(args: Array[String]): Unit = {
    val b = new Scala2CompletionBench()
    b.setup()
    val result = b.complete()
    println(s"completion count: ${result.getItems.size()}")
    result.getItems.asScala.take(20).foreach(item => println(s"  ${item.getLabel}"))
    b.tearDown()
  }
}
