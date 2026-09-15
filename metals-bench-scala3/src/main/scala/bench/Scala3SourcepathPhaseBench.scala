package bench

import java.nio.file.Files
import java.util.concurrent.TimeUnit

import scala.compiletime.uninitialized
import scala.io.Codec
import scala.jdk.CollectionConverters.*

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interactive.CachedLogicalPackage
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.parsing.Parsers
import dotty.tools.dotc.util.SourceFile
import dotty.tools.io.AbstractFile

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

/**
 * Decomposes Scala3SourcepathBench's ~7.6x-slower-than-Scala-2 result into
 * its phases: reading each file into a `SourceFile` vs. outline-parsing it.
 * Package-tree building (`SourceFileTraverser`) isn't isolated here since
 * it's private to `LogicalPackagesProvider`; its share of the cost is
 * (full `root()` time from Scala3SourcepathBench) - (`parseOnly` here).
 *
 * `SourceFile`s are constructed and read ONCE in @Setup (`.content()` is
 * forced so the lazy read happens up front, not on first parse), and every
 * @Benchmark reuses those same instances. This matters: mtags' Scala 2
 * equivalent caches its read `BatchSourceFile`s in a `lazy val` on the
 * `Global` instance, so repeated calls to `parseSourcePath()` reuse
 * already-read content -- if this benchmark constructed a fresh `SourceFile`
 * per iteration instead, it would silently re-read every file from disk on
 * every iteration (confirmed via profiling: ~80% of samples landed in
 * `__open`/`read`/`close`), which isn't a fair comparison of the parsers.
 */
@State(Scope.Benchmark)
class Scala3SourcepathPhaseBench {

  private val packages = 50
  private val filesPerPackage = 20
  private val methodsPerFile = 10

  private var sourceFiles: List[SourceFile] = uninitialized
  private var ctx: Context = uninitialized
  // Isolates the cost of dotc's significant-indentation tracking: same
  // corpus, same driver, only `-no-indent` added.
  private var ctxNoIndent: Context = uninitialized
  // Additionally drops doc comment retention (`-Xdrop-comments`), which the
  // Scanner otherwise keeps a `Map` of alongside every token.
  private var ctxNoIndentNoComments: Context = uninitialized

  private def newCtx(sourcePath: String, extraFlags: List[String]): Context =
    val classpath = sys.props("java.class.path")
    val driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath, "-sourcepath", sourcePath, "-Ylogical-package-loading") ++ extraFlags,
      CachedLogicalPackage.none,
    )
    driver.currentCtx

  @Setup
  def setup(): Unit =
    val dir = Files.createTempDirectory("scala3-sourcepath-phase-bench")
    SourcepathCorpus.generate(dir, packages, filesPerPackage, methodsPerFile)

    ctx = newCtx(dir.toString, Nil)
    ctxNoIndent = newCtx(dir.toString, List("-no-indent"))
    ctxNoIndentNoComments = newCtx(dir.toString, List("-no-indent", "-Xdrop-comments"))

    val files = Files
      .walk(dir)
      .iterator()
      .asScala
      .filter(p => p.toString.endsWith(".scala"))
      .map(p => AbstractFile.getFile(p.toString))
      .toList

    given Context = ctx
    sourceFiles = files.map { f =>
      val sf = SourceFile(f, ctx.settings.sourceroot.value, Codec(ctx.settings.encoding.value))
      sf.content() // force the lazy read now, not on first parse
      sf
    }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def parseOnly(bh: Blackhole): Unit =
    given Context = ctx
    for sf <- sourceFiles do bh.consume(new Parsers.OutlineParser(sf).parse())

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def parseOnlyNoIndent(bh: Blackhole): Unit =
    given Context = ctxNoIndent
    for sf <- sourceFiles do bh.consume(new Parsers.OutlineParser(sf).parse())

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def parseOnlyNoIndentNoComments(bh: Blackhole): Unit =
    given Context = ctxNoIndentNoComments
    for sf <- sourceFiles do bh.consume(new Parsers.OutlineParser(sf).parse())

}
