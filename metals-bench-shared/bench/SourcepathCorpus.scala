package bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates a synthetic sourcepath tree used to compare the Scala 2 (mtags)
 * and Scala 3 (dotc) implementations of "scan a sourcepath and build a
 * package -> sources index by outline-parsing every file" head to head.
 *
 * The generated syntax is restricted to a subset that both the Scala 2 and
 * Scala 3 outline parsers accept (no `given`/`enum`/top-level definitions),
 * so both benchmarks parse byte-for-byte identical input.
 */
object SourcepathCorpus {

  def generate(
      root: Path,
      packages: Int,
      filesPerPackage: Int,
      methodsPerFile: Int,
  ): Path = {
    for (p <- 0 until packages) {
      val pkgName = s"bench.generated.pkg$p"
      val pkgDir = root.resolve(pkgName.replace('.', '/'))
      Files.createDirectories(pkgDir)
      for (f <- 0 until filesPerPackage) {
        val className = s"Gen${p}_$f"
        val methods = (0 until methodsPerFile)
          .map { m =>
            s"""  def method$m(x: Int): Int = {
               |    val a = x + $m
               |    val b = a * 2 - $m
               |    if (b > 0) b else -b
               |  }""".stripMargin
          }
          .mkString("\n\n")
        val content =
          s"""package $pkgName
             |
             |class $className(x: Int) {
             |$methods
             |}
             |
             |object $className {
             |  def apply(x: Int): $className = new $className(x)
             |}
             |""".stripMargin
        Files.write(
          pkgDir.resolve(s"$className.scala"),
          content.getBytes(StandardCharsets.UTF_8),
        )
      }
    }
    root
  }
}
