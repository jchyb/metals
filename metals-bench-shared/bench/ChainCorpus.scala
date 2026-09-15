package bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates `chainCount` independent inheritance chains, each `depth` classes
 * deep, where `TargetK extends Target(K-1)` via a FULLY-QUALIFIED reference
 * (no imports, to avoid reintroducing the already-resolved import-specific
 * confound from earlier in this investigation). Each class is in its own
 * package, so resolving the deepest (leaf) class forces the compiler to
 * lazily resolve every ancestor in the chain via SourcefileLoader/
 * compileLate, one after another -- unlike SymbolResolutionCorpus, where
 * each target is fully independent (no cross-references at all).
 *
 * Returns the fully-qualified name of each chain's leaf (deepest) class, in
 * generation order, so callers can consume a fresh, never-before-touched
 * chain per benchmark invocation.
 */
object ChainCorpus {

  def generate(root: Path, chainCount: Int, depth: Int): IndexedSeq[String] = {
    for (c <- 0 until chainCount) yield {
      var parentFqcn: Option[String] = None
      for (k <- 0 until depth) {
        val pkgName = s"chaincorpus.chain$c.target$k"
        val className = s"Target$k"
        val pkgDir = root.resolve(pkgName.replace('.', '/'))
        Files.createDirectories(pkgDir)
        val extendsClause = parentFqcn.map(p => s" extends $p").getOrElse("")
        val content =
          s"""package $pkgName
             |
             |class $className$extendsClause {
             |  def method0(a: Int): Int = a + $k
             |}
             |""".stripMargin
        Files.write(
          pkgDir.resolve(s"$className.scala"),
          content.getBytes(StandardCharsets.UTF_8),
        )
        parentFqcn = Some(s"$pkgName.$className")
      }
      parentFqcn.get
    }
  }
}
