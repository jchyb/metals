package bench

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import dotty.tools.dotc.Driver
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interactive.CachedLogicalPackage
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.reporting.Diagnostic

/**
 * Checks whether the spurious NotClassType/NotAMember-via-import behavior
 * found in Scala3SymbolResolutionBench.resolveOneSymbolViaImport is specific
 * to `-sourcepath`'s lazy `SourcefileLoader` loading, or a broader property
 * of resolving a not-yet-typechecked class through an import.
 *
 * Run with: benchSourcepathScala3/runMain bench.TestNoSourcepath
 */
object TestNoSourcepath:

  def main(args: Array[String]): Unit =
    val classpath = sys.props("java.class.path")
    val dir = Files.createTempDirectory("scala3-no-sourcepath-test")

    val targetFile = dir.resolve("Target.scala")
    Files.write(
      targetFile,
      "package p\nclass Target {\n  def method0(a: Int): Int = a\n}\n".getBytes(StandardCharsets.UTF_8),
    )
    val importerFile = dir.resolve("Importer.scala")
    Files.write(
      importerFile,
      "import p.Target\nclass Importer extends Target\n".getBytes(StandardCharsets.UTF_8),
    )

    // --- Scenario 1: plain two-file batch compile, no InteractiveDriver, no -sourcepath ---
    println("=== Scenario 1: plain batch compile (dotc Target.scala Importer.scala) ===")
    val driver1 = new Driver
    val reporter1 = driver1.process(
      Array("-classpath", classpath, "-color:never", targetFile.toString, importerFile.toString)
    )
    println(s"errors: ${reporter1.errorCount}, warnings: ${reporter1.warningCount}")

    // --- Scenario 2: InteractiveDriver, target explicitly compiled first (no sourcepath),
    // then referenced via import+simple-name in a SEPARATE driver.run call ---
    println("\n=== Scenario 2: InteractiveDriver, target pre-compiled via explicit driver.run (no -sourcepath) ===")
    val driver2 = new InteractiveDriver(List("-color:never", "-classpath", classpath), CachedLogicalPackage.none)
    val targetUri = URI.create("file:///Target.scala")
    val diags1 = driver2.run(targetUri, "package p\nclass Target {\n  def method0(a: Int): Int = a\n}\n")
    println(s"after compiling Target.scala: ${diags1.size} diagnostics")
    diags1.foreach(d => println(s"  [target] ${d.message}"))

    val importerUri = URI.create("file:///Importer.scala")
    val start = System.nanoTime()
    val diags2 = driver2.run(importerUri, "import p.Target\nclass Importer extends Target")
    val elapsedMs = (System.nanoTime() - start) / 1000000
    println(s"after compiling Importer.scala referencing already-opened Target: ${diags2.size} diagnostics, ${elapsedMs}ms")
    diags2.foreach(d => println(s"  [importer] ${d.message}"))
