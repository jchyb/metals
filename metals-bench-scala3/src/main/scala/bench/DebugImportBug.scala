package bench

import java.net.URI
import java.nio.file.Files

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interactive.CachedLogicalPackage
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.interactive.LogicalPackagesProvider

/**
 * Minimal repro of the resolveOneSymbolViaImport spurious-error scenario,
 * for use with the instrumented (DOTC_DEBUG_CHECKCLASSTYPE-gated) local
 * scala3 build to observe live values at the failure point.
 *
 * Run with: DOTC_DEBUG_CHECKCLASSTYPE=1 benchSourcepathScala3/runMain bench.DebugImportBug
 */
object DebugImportBug:

  def main(args: Array[String]): Unit =
    val classpath = sys.props("java.class.path")
    val dir = Files.createTempDirectory("scala3-debug-import-bug")
    val targets = SymbolResolutionCorpus.generate(dir, 1)
    val fqcn = targets(0)
    val simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1)

    val sourcePath = dir.toString
    def extractor(using Context) = Some(new LogicalPackagesProvider(sourcePath).root)
    val driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath, "-sourcepath", sourcePath, "-Ylogical-package-loading"),
      CachedLogicalPackage(extractor),
    )

    val uri = URI.create("file:///Importer.scala")
    println(s"resolving $fqcn (simple name $simpleName) via import + extends...")
    val diags = driver.run(uri, s"import $fqcn\nclass Importer extends $simpleName")
    println(s"diagnostics: ${diags.size}")
    diags.foreach(d => println(s"  ${d.message}"))
