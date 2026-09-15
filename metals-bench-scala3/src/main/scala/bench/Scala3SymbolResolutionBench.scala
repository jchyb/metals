package bench

import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit

import scala.compiletime.uninitialized

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interactive.CachedLogicalPackage
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.interactive.LogicalPackagesProvider

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

/**
 * Benchmarks the DEEPER step beyond outline-scanning (see
 * Scala3SourcepathBench/Scala3SourcepathPhaseBench): actually resolving one
 * symbol that's known only via the sourcepath index, forcing a real
 * parse+typecheck of its file. This goes through the exact production
 * mechanism: `driver.run` on a fresh "importer" snippet that references a
 * target class forces the compiler to resolve that name, which hits
 * `SymbolLoaders.initializeFromClassPath`'s source-only branch (`(None,
 * Some(src))`, core/SymbolLoaders.scala:217-219) -> `enterToplevelsFromSource`
 * installs a `SourcefileLoader` completer -> `SourcefileLoader.doComplete`
 * (SymbolLoaders.scala:573-575) calls `ctx.run.nn.lateCompile(srcfile,
 * typeCheck = true)` (InteractiveDriver always sets `YretainTrees`, so
 * `typeCheck` is always true) -> `Run.lateCompile` (Run.scala:497) really
 * parses+typechecks the target file.
 *
 * Uses `Mode.SingleShotTime` with one never-before-referenced target class
 * per invocation: once a symbol is resolved, its completer is permanently
 * replaced (see the SymDenotation.completeOnce discussion earlier), so a
 * second reference would be a cache hit, not a fresh resolution. `targetCount`
 * must therefore cover every warmup+measurement invocation within a single
 * fork (each fork gets a fresh JVM and a fresh corpus via @Setup(Level.Trial),
 * so it doesn't need to cover *all* forks, just one).
 */
@State(Scope.Benchmark)
class Scala3SymbolResolutionBench {

  private val targetCount = 200

  private var driver: InteractiveDriver = uninitialized
  private var targets: IndexedSeq[String] = uninitialized
  private var nextIndex = 0

  @Setup(Level.Trial)
  def setup(): Unit =
    val dir = Files.createTempDirectory("scala3-symres-bench")
    targets = SymbolResolutionCorpus.generate(dir, targetCount)

    val classpath = sys.props("java.class.path")
    val sourcePath = dir.toString
    def extractor(using Context) = Some(new LogicalPackagesProvider(sourcePath).root)
    driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath, "-sourcepath", sourcePath, "-Ylogical-package-loading"),
      CachedLogicalPackage(extractor),
    )

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def resolveOneSymbol(bh: Blackhole): Unit =
    val i = nextIndex
    nextIndex += 1
    val fqcn = targets(i % targets.length)
    val uri = URI.create(s"file:///Importer$i.scala")
    // No `import` statement -- reference the fully-qualified name directly,
    // to isolate symbol resolution from import-suggestion/import-resolution
    // machinery (see ImportSuggestions.lookInside in the profile).
    val diags = driver.run(uri, s"class Importer$i extends $fqcn")
    bh.consume(diags)

  /** Baseline: same `driver.run` call shape, but the snippet references
   *  nothing on the sourcepath, so no `SourcefileLoader`/`lateCompile` ever
   *  triggers. Isolates fixed per-call overhead in `driver.run` itself
   *  (fresh `Run`/`Context`/root-imports setup, etc.) from the actual cost
   *  of resolving a sourcepath-only symbol.
   */
  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def runOnlyBaseline(bh: Blackhole): Unit =
    val i = nextIndex
    nextIndex += 1
    val uri = URI.create(s"file:///Empty$i.scala")
    val diags = driver.run(uri, s"class Empty$i")
    bh.consume(diags)

  /** Same target-resolution as `resolveOneSymbol`, but referenced via an
   *  `import` + simple name instead of a fully-qualified reference. Found to
   *  cost ~22ms more than `resolveOneSymbol` (which is ~free beyond
   *  `runOnlyBaseline`) -- this isolates whether that cost comes from merely
   *  having the import (see `importOnlyUnused`) or from the combination of
   *  importing *and* using the imported name.
   */
  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def resolveOneSymbolViaImport(bh: Blackhole): Unit =
    val i = nextIndex
    nextIndex += 1
    val fqcn = targets(i % targets.length)
    val simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1)
    val uri = URI.create(s"file:///Importer$i.scala")
    val diags = driver.run(uri, s"import $fqcn\nclass Importer$i extends $simpleName")
    bh.consume(diags)

  /** An `import` of a source-only symbol, never subsequently used. If this
   *  is also slow, the cost is inherent to processing the import itself
   *  (e.g. import-suggestion machinery); if it's fast (like
   *  `resolveOneSymbol`), the cost only shows up when the imported name is
   *  actually looked up afterward.
   */
  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def importOnlyUnused(bh: Blackhole): Unit =
    val i = nextIndex
    nextIndex += 1
    val fqcn = targets(i % targets.length)
    val uri = URI.create(s"file:///Importer$i.scala")
    val diags = driver.run(uri, s"import $fqcn\nobject Importer$i")
    bh.consume(diags)

  /** Same target-resolution as `resolveOneSymbolViaImport`, but `TargetN` is
   *  used in ordinary code (a val's type + a `new` expression) instead of as
   *  a supertype in an `extends` clause. The spurious NotClassType/NotAMember
   *  error was traced to `Namer.ClassCompleter.checkedParentType` ->
   *  `Checking.checkClassType` (Namer.scala:1718 area, Checking.scala:1245),
   *  which ONLY runs for parent types during class completion in the Namer
   *  phase -- ordinary body typechecking (this benchmark) never calls it.
   *  If this is fast, the bug is specific to parent-type-checking; if it's
   *  also slow, the bug is a broader property of resolving a lazily-loaded
   *  symbol via import, independent of where it's used.
   */
  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def resolveOneSymbolInBodyViaImport(bh: Blackhole): Unit =
    val i = nextIndex
    nextIndex += 1
    val fqcn = targets(i % targets.length)
    val simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1)
    val uri = URI.create(s"file:///Importer$i.scala")
    val diags = driver.run(uri, s"import $fqcn\nclass Importer$i { val x: $simpleName = new $simpleName() }")
    bh.consume(diags)

}
