package bench

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
 * Benchmarks `dotty.tools.dotc.interactive.LogicalPackagesProvider.root`, the
 * outline-parse-based sourcepath scan the Scala 3 presentation compiler uses
 * (under `-Ylogical-package-loading`) to build a package -> sources index for
 * IDE tooling. Compare against `Scala2SourcepathBench` in `metals-bench`,
 * mtags' port of the same mechanism, run on the same generated corpus.
 *
 * Driver/Context construction is done once in @Setup, so only the sourcepath
 * scan itself (the part that scales with corpus size) is measured.
 */
@State(Scope.Benchmark)
class Scala3SourcepathBench {

  private val packages = 50
  private val filesPerPackage = 20
  private val methodsPerFile = 10

  private var sourcePath: String = uninitialized
  private var ctx: Context = uninitialized

  @Setup
  def setup(): Unit =
    val dir = Files.createTempDirectory("scala3-sourcepath-bench")
    SourcepathCorpus.generate(dir, packages, filesPerPackage, methodsPerFile)
    sourcePath = dir.toString
    // Core definitions (scala.Any, scala.AnyRef, ...) are resolved eagerly when the
    // driver's Context is built, so it needs a real classpath; reuse this JVM's own
    // launch classpath, which already has scala3-library/scala-library on it.
    val classpath = sys.props("java.class.path")
    val driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath, "-sourcepath", sourcePath, "-Ylogical-package-loading"),
      CachedLogicalPackage.none,
    )
    ctx = driver.currentCtx

  @TearDown
  def teardown(): Unit = ()

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def scanSourcePath(bh: Blackhole): Unit =
    val provider = new LogicalPackagesProvider(sourcePath)
    bh.consume(provider.root(using ctx))

}
