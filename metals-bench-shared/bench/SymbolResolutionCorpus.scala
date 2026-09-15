package bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates one target class per package (`bench.generated.symres.targetN.TargetN`),
 * for benchmarking the DEEPER "resolve one symbol from an unopened sourcepath
 * file" step (as opposed to SourcepathCorpus, which is for the shallow
 * outline-scan step). Each target lives in its own package so referencing it
 * also forces a fresh, never-before-touched package load, matching a
 * realistic "first reference to this symbol in this session" cost.
 *
 * Returns the fully-qualified class names, in generation order, so callers
 * can consume a fresh, never-before-referenced target per benchmark
 * invocation (once a symbol is resolved once, it's cached for the rest of
 * the session -- see the SourceFile-caching discussion for why that matters
 * for benchmark design).
 */
object SymbolResolutionCorpus {

  def generate(root: Path, count: Int): IndexedSeq[String] =
    for (i <- 0 until count) yield {
      val pkgName = s"symrescorpus.target$i"
      val className = s"Target$i"
      val pkgDir = root.resolve(pkgName.replace('.', '/'))
      Files.createDirectories(pkgDir)
      val content =
        s"""package $pkgName
           |
           |class $className {
           |  def method0(a: Int): Int = a + $i
           |  def method1(a: Int): Int = a * 2 - $i
           |  def method2(a: Int): Int = if (a > 0) method0(a) else method1(a)
           |}
           |""".stripMargin
      Files.write(
        pkgDir.resolve(s"$className.scala"),
        content.getBytes(StandardCharsets.UTF_8),
      )
      s"$pkgName.$className"
    }
}
