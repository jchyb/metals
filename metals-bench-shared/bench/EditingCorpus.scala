package bench

/**
 * Generates a moderately-sized, self-contained source file (no cross-file
 * dependencies, no sourcepath involved) representing a typical open editor
 * buffer, plus a way to produce a slightly "edited" variant of it (one
 * changed literal per call) -- for benchmarking the cost of re-typechecking
 * an already-open file after a small edit, which is the single most common
 * operation in a real editing session (far more common than resolving a
 * brand-new symbol).
 */
object EditingCorpus {

  val methodCount = 30

  /** `edit` selects which variant to produce; the compiler must fully
   *  re-parse+typecheck the file each time regardless of how small the
   *  textual change is, so varying one literal per call is representative
   *  of the real cost of "the user typed one character."
   */
  def codeFor(edit: Int): String = {
    val methods = (0 until methodCount)
      .map { m =>
        s"""  def method$m(x: Int): Int = {
           |    val a = x + $m
           |    val b = a * 2 - $m
           |    val c = if (b > 0) a else b
           |    val d = c * $edit + $m
           |    if (d > 0) d else -d
           |  }""".stripMargin
      }
      .mkString("\n\n")
    s"""class Editing {
       |$methods
       |}
       |""".stripMargin
  }
}
