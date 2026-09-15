package bench

/**
 * Prints the generated Main.scala content for a given index/package, to
 * check for string-interpolation escaping mistakes without needing to
 * actually typecheck it.
 *
 * Run with: bench/runMain bench.DebugPrintMainCode
 */
object DebugPrintMainCode {
  def main(args: Array[String]): Unit = {
    println(SourcepathCompletionCorpus.mainCode(0, "somepkg.target0"))
  }
}
