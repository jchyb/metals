package bench

import java.nio.file.Files
import java.util.concurrent.TimeUnit

import scala.reflect.internal.util.BatchSourceFile

import scala.tools.nsc.ParsedLogicalPackage
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.ConsoleReporter

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

/**
 * Scala 2 counterpart to Scala3SymbolResolutionBench: forces a real
 * parse+typecheck of a source-only sourcepath file by compiling a fresh
 * "importer" snippet that references it -- the same production mechanism
 * mtags relies on: a source-only classpath entry gets a `SourcefileLoader`
 * (nsc/symtab/SymbolLoaders.scala), whose `doComplete` calls
 * `currentRun.compileLate(srcfile)` (nsc/symtab/GlobalSymbolLoaders.scala),
 * which is `Run.compileLate` (nsc/Global.scala:1689-1704) -- a plain public
 * method on an already-initialized `Run`, driving the unit "at least to
 * phase typer" (bounded here via `-Ystop-after:typer` to match the Scala 3
 * side, which never runs past typechecking either).
 *
 * `Global`/`Run` are built once in @Setup so the classpath/definitions-init
 * cost isn't measured; `resolveOneSymbol` reuses that same `Global` across
 * invocations (packages/symbols persist across separate `Run`s on one
 * `Global`, same as separate `driver.run` calls share one `ContextBase` on
 * the Scala 3 side) but starts a fresh `Run` per invocation, mirroring one
 * hover/completion request each.
 */
@State(Scope.Benchmark)
class Scala2SymbolResolutionBench {

  private val targetCount = 200

  private var global: ParsedLogicalPackage.OutlineParseCompiler = _
  private var targets: IndexedSeq[String] = _
  private var nextIndex = 0

  @Setup(Level.Trial)
  def setup(): Unit = {
    val dir = Files.createTempDirectory("scala2-symres-bench")
    targets = SymbolResolutionCorpus.generate(dir, targetCount)

    val settings = new Settings(println)
    settings.usejavacp.value = true
    settings.sourcepath.value = dir.toString
    settings.stopAfter.value = List("typer")

    val g = new ParsedLogicalPackage.OutlineParseCompiler(settings, new ConsoleReporter(settings))
    new g.Run
    global = g
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def resolveOneSymbol(bh: Blackhole): Unit = {
    val i = nextIndex
    nextIndex += 1
    val fqcn = targets(i % targets.length)
    // No `import` statement -- reference the fully-qualified name directly,
    // matching Scala3SymbolResolutionBench, to isolate symbol resolution
    // from import-resolution machinery.
    val importer = new BatchSourceFile(
      s"Importer$i.scala",
      s"class Importer$i extends $fqcn"
    )
    val g = global
    val run = new g.Run
    run.compileSources(List(importer))
    bh.consume(run)
  }

  /** Baseline: same `new Run().compileSources` call shape, but the snippet
   *  references nothing on the sourcepath, so `SourcefileLoader.doComplete`
   *  never triggers. Isolates fixed per-call overhead in constructing/running
   *  a `Run` from the actual cost of resolving a sourcepath-only symbol.
   */
  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def runOnlyBaseline(bh: Blackhole): Unit = {
    val i = nextIndex
    nextIndex += 1
    val importer = new BatchSourceFile(s"Empty$i.scala", s"class Empty$i")
    val g = global
    val run = new g.Run
    run.compileSources(List(importer))
    bh.consume(run)
  }

  /** Same target-resolution as `resolveOneSymbol`, but referenced via an
   *  `import` + simple name instead of a fully-qualified reference. Mirrors
   *  Scala3SymbolResolutionBench.resolveOneSymbolViaImport, which found a
   *  ~20ms cost specific to resolving a simple name through an import of a
   *  source-only symbol (vs. ~0.17ms for a fully-qualified reference) --
   *  this checks whether Scala 2 has an analogous gap.
   */
  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def resolveOneSymbolViaImport(bh: Blackhole): Unit = {
    val i = nextIndex
    nextIndex += 1
    val fqcn = targets(i % targets.length)
    val simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1)
    val importer = new BatchSourceFile(
      s"Importer$i.scala",
      s"import $fqcn\nclass Importer$i extends $simpleName"
    )
    val g = global
    val run = new g.Run
    run.compileSources(List(importer))
    bh.consume(run)
  }

  /** An `import` of a source-only symbol, never subsequently used. */
  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def importOnlyUnused(bh: Blackhole): Unit = {
    val i = nextIndex
    nextIndex += 1
    val fqcn = targets(i % targets.length)
    val importer = new BatchSourceFile(
      s"Importer$i.scala",
      s"import $fqcn\nobject Importer$i"
    )
    val g = global
    val run = new g.Run
    run.compileSources(List(importer))
    bh.consume(run)
  }

  /** Scala 2 counterpart to Scala3SymbolResolutionBench.resolveOneSymbolInBodyViaImport:
   *  `TargetN` used in ordinary code (a val's type + a `new` expression)
   *  instead of as a supertype in an `extends` clause.
   */
  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def resolveOneSymbolInBodyViaImport(bh: Blackhole): Unit = {
    val i = nextIndex
    nextIndex += 1
    val fqcn = targets(i % targets.length)
    val simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1)
    val importer = new BatchSourceFile(
      s"Importer$i.scala",
      s"import $fqcn\nclass Importer$i { val x: $simpleName = new $simpleName() }"
    )
    val g = global
    val run = new g.Run
    run.compileSources(List(importer))
    bh.consume(run)
  }

}
