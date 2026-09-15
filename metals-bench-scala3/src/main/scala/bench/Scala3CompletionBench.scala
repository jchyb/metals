package bench

import java.net.URI
import java.util.concurrent.TimeUnit

import scala.compiletime.uninitialized

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interactive.CachedLogicalPackage
import dotty.tools.dotc.interactive.Completion
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.util.SourcePosition
import dotty.tools.dotc.util.Spans

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

/**
 * Benchmarks dotc's own completion computation directly (`Completion.completions`),
 * built against THIS local scala3 checkout ("main"), not a published release --
 * unlike the earlier CompletionScopeOpenBench attempt, which mistakenly reused
 * a stale `@Param(Array("3.3.1", ...))` from a pre-existing benchmark file and
 * never actually tested this checkout at all.
 *
 * The buffer is typechecked once in @Setup; only the completion computation
 * itself (given an already-typed unit, as in real usage where the buffer
 * hasn't changed since the last request) is timed.
 */
@State(Scope.Benchmark)
class Scala3CompletionBench {

  private val code = "object Main {\n  def foo = \"\"\n  fo\n}\n"
  private val offset = code.indexOf("  fo\n") + "  fo".length
  private val uri = URI.create("file:///Main.scala")

  private var driver: InteractiveDriver = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    val classpath = sys.props("java.class.path")
    driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath),
      CachedLogicalPackage.none,
    )
    driver.run(uri, code)

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def complete(bh: Blackhole): Unit =
    given ctx: Context = driver.currentCtx
    val unit = driver.compilationUnits(uri)
    given newCtx: Context = ctx.fresh.setCompilationUnit(unit).setPhase(ctx.base.typerPhase)
    val pos = SourcePosition(unit.source, Spans.Span(offset))
    bh.consume(Completion.completions(pos)(using newCtx))

}
