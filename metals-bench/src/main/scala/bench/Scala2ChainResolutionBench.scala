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
 * Scala 2 counterpart to Scala3ChainResolutionBench: resolving the deepest
 * class in an inheritance chain of sourcepath-only classes, forcing a
 * cascade of lazy compiles (SourcefileLoader -> Run.compileLate) through
 * every ancestor.
 */
@State(Scope.Benchmark)
class Scala2ChainResolutionBench {

  private val chainCount = 60
  private val depth = 20

  private var global: ParsedLogicalPackage.OutlineParseCompiler = _
  private var leaves: IndexedSeq[String] = _
  private var nextIndex = 0

  @Setup(Level.Trial)
  def setup(): Unit = {
    val dir = Files.createTempDirectory("scala2-chain-bench")
    leaves = ChainCorpus.generate(dir, chainCount, depth)

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
  def resolveChainLeaf(bh: Blackhole): Unit = {
    val i = nextIndex
    nextIndex += 1
    val leafFqcn = leaves(i % leaves.length)
    val importer = new BatchSourceFile(
      s"Importer$i.scala",
      s"class Importer$i extends $leafFqcn"
    )
    val g = global
    val run = new g.Run
    run.compileSources(List(importer))
    bh.consume(run)
  }

}
