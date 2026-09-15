package bench

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

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
 * Unlike Scala2SourcepathBench (which builds `Global` once and lets its
 * `lazy val sourceRoots` cache file content across every measured
 * iteration), this rebuilds a fresh `Global` before EVERY invocation
 * (`@Setup(Level.Invocation)`, excluded from the timed score), so every
 * measured call is a genuinely cold first read+parse+traverse -- directly
 * comparable to Scala3SourcepathBench.scanSourcePath, which is always cold
 * by construction (dotc's `LogicalPackagesProvider.root()` has no
 * cross-call cache to begin with).
 */
@State(Scope.Benchmark)
class Scala2SourcepathColdBench {

  private val packages = 50
  private val filesPerPackage = 20
  private val methodsPerFile = 10

  private var corpusDir: Path = _
  private var global: ParsedLogicalPackage.OutlineParseCompiler = _

  @Setup(Level.Trial)
  def setupCorpus(): Unit = {
    val dir = Files.createTempDirectory("scala2-sourcepath-cold-bench")
    SourcepathCorpus.generate(dir, packages, filesPerPackage, methodsPerFile)
    corpusDir = dir
  }

  @Setup(Level.Invocation)
  def setupFreshGlobal(): Unit = {
    val settings = new Settings(println)
    settings.usejavacp.value = true
    settings.sourcepath.value = corpusDir.toString

    val g = new ParsedLogicalPackage.OutlineParseCompiler(settings, new ConsoleReporter(settings))
    new g.Run
    global = g
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def coldScanSourcePath(bh: Blackhole): Unit = {
    bh.consume(global.parseSourcePath())
  }

}
