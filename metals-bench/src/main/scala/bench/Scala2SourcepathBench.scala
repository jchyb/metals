package bench

import java.nio.file.Files
import java.util.concurrent.TimeUnit

import scala.tools.nsc.ParsedLogicalPackage
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.ConsoleReporter

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole

/**
 * Benchmarks mtags' `LogicalPackagesProvider.parseSourcePath`, the Scala 2
 * port of the same outline-parse-based sourcepath scan benchmarked in
 * [[Scala3SourcepathBench]] (same corpus generator, same shape of work), so
 * the two can be compared head to head.
 *
 * The `Global` frontend is built once in @Setup (mirroring how the Scala 3
 * benchmark builds its `Context` once), so only the scan itself is measured.
 */
@State(Scope.Benchmark)
class Scala2SourcepathBench {

  private val packages = 50
  private val filesPerPackage = 20
  private val methodsPerFile = 10

  private var global: ParsedLogicalPackage.OutlineParseCompiler = _

  @Setup
  def setup(): Unit = {
    val dir = Files.createTempDirectory("scala2-sourcepath-bench")
    SourcepathCorpus.generate(dir, packages, filesPerPackage, methodsPerFile)

    val settings = new Settings(println)
    settings.usejavacp.value = true
    settings.sourcepath.value = dir.toString

    val g = new ParsedLogicalPackage.OutlineParseCompiler(settings, new ConsoleReporter(settings))
    // definitions (scala.Any, scala.AnyRef, ...) must be initialized before parsing, see
    // ParsedLogicalPackage.collectLogicalPackages in mtags for the same requirement.
    new g.Run
    global = g
  }

  @TearDown
  def teardown(): Unit = {}

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def scanSourcePath(bh: Blackhole): Unit = {
    bh.consume(global.parseSourcePath())
  }

}
