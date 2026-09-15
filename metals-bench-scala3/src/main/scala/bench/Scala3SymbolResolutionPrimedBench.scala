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
 * Tests a concrete workaround for the ~24ms marginal cost found in
 * Scala3SymbolResolutionBench.resolveOneSymbolViaImport: does forcing the
 * target symbol to be fully completed BEFORE it's used as a parent type via
 * import (as opposed to lazily completing it right there, mid-`checkedParentType`)
 * avoid the spurious NotClassType/NotAMember + discarded import-suggestion
 * search?
 *
 * The "priming" touch (`class Prime$i extends $fqcn`, a fully-qualified
 * reference -- shown earlier to be cheap and to never trigger the bug) is
 * done in @Setup(Level.Invocation), which JMH excludes from the timed score,
 * so only the subsequent import+extends resolution is measured. This lives
 * in its own @State class so the extra priming call doesn't perturb
 * Scala3SymbolResolutionBench's other benchmarks (which share one @State and
 * would otherwise all pay for -- and have their target-index bookkeeping
 * disrupted by -- a setup step meant for only one of them).
 */
@State(Scope.Benchmark)
class Scala3SymbolResolutionPrimedBench {

  private val targetCount = 200

  private var driver: InteractiveDriver = uninitialized
  private var targets: IndexedSeq[String] = uninitialized
  private var nextIndex = 0
  private var primedFqcn: String = uninitialized
  private var primedIndex = 0

  @Setup(Level.Trial)
  def setup(): Unit =
    val dir = Files.createTempDirectory("scala3-symres-primed-bench")
    targets = SymbolResolutionCorpus.generate(dir, targetCount)

    val classpath = sys.props("java.class.path")
    val sourcePath = dir.toString
    def extractor(using Context) = Some(new LogicalPackagesProvider(sourcePath).root)
    driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath, "-sourcepath", sourcePath, "-Ylogical-package-loading"),
      CachedLogicalPackage(extractor),
    )

  /** Not timed: forces the next target's symbol to be fully completed via a
   *  fully-qualified reference before the timed benchmark touches it.
   */
  @Setup(Level.Invocation)
  def primeNext(): Unit =
    val i = nextIndex
    nextIndex += 1
    val fqcn = targets(i % targets.length)
    primedFqcn = fqcn
    primedIndex = i
    val primeUri = URI.create(s"file:///Prime$i.scala")
    driver.run(primeUri, s"class Prime$i extends $fqcn")

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def resolveOneSymbolViaImportPrimed(bh: Blackhole): Unit =
    val fqcn = primedFqcn
    val simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1)
    val uri = URI.create(s"file:///Importer$primedIndex.scala")
    val diags = driver.run(uri, s"import $fqcn\nclass Importer$primedIndex extends $simpleName")
    bh.consume(diags)

}
