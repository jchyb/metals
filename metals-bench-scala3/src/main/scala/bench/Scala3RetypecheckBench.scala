package bench

import java.net.URI
import java.util.concurrent.TimeUnit

import scala.compiletime.uninitialized

import dotty.tools.dotc.interactive.CachedLogicalPackage
import dotty.tools.dotc.interactive.InteractiveDriver

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

/**
 * Benchmarks the single most common real IDE operation: re-typechecking an
 * already-open buffer after a small edit (the user types a character).
 * Unlike everything else in this suite, this has nothing to do with
 * sourcepath/classpath symbol discovery -- it's a self-contained file with no
 * cross-file references, exercising plain parse+typecheck cost.
 *
 * Each invocation calls `driver.run` on the SAME uri with slightly different
 * content (one changed literal), mirroring `CachingDriver`'s behavior on a
 * genuine edit (content differs from the last compile, so it does a real,
 * full re-typecheck -- there is no finer-grained incremental recompilation
 * in dotc's interactive driver today).
 */
@State(Scope.Benchmark)
class Scala3RetypecheckBench {

  private var driver: InteractiveDriver = uninitialized
  private var nextEdit = 0

  @Setup(Level.Trial)
  def setup(): Unit =
    val classpath = sys.props("java.class.path")
    driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath),
      CachedLogicalPackage.none,
    )
    // Warm the buffer once so subsequent calls are genuine re-edits, not a
    // first-open.
    driver.run(URI.create("file:///Editing.scala"), EditingCorpus.codeFor(-1))

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def retypecheckAfterEdit(bh: Blackhole): Unit =
    val edit = nextEdit
    nextEdit += 1
    val diags = driver.run(URI.create("file:///Editing.scala"), EditingCorpus.codeFor(edit))
    bh.consume(diags)

}
