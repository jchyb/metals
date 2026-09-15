package bench

import java.util.concurrent.TimeUnit

import scala.reflect.internal.util.BatchSourceFile

import scala.tools.nsc.Global
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
 * Scala 2 counterpart to Scala3RetypecheckBench: re-typechecks the same
 * self-contained "open buffer" after a small edit, on a plain `Global`
 * (no sourcepath/logical-package machinery involved at all, matching the
 * Scala 3 side -- this is testing raw same-file parse+typecheck cost, the
 * most common real IDE operation).
 */
@State(Scope.Benchmark)
class Scala2RetypecheckBench {

  private var global: Global = _
  private var nextEdit = 0

  @Setup(Level.Trial)
  def setup(): Unit = {
    val settings = new Settings(println)
    settings.usejavacp.value = true
    settings.stopAfter.value = List("typer")

    val g = new Global(settings, new ConsoleReporter(settings))
    val run = new g.Run
    run.compileSources(List(new BatchSourceFile("Editing.scala", EditingCorpus.codeFor(-1))))
    global = g
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def retypecheckAfterEdit(bh: Blackhole): Unit = {
    val edit = nextEdit
    nextEdit += 1
    val g = global
    val run = new g.Run
    run.compileSources(List(new BatchSourceFile("Editing.scala", EditingCorpus.codeFor(edit))))
    bh.consume(run)
  }

}
