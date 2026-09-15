package bench

import java.util.concurrent.TimeUnit

import org.eclipse.lsp4j.CompletionList
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
class Scala3PcCompletionBench extends PcBenchmark {

  private val scalaVersion = "3.10.1-RC1-bin-SNAPSHOT"

  private val request = SourceRequest.fromPath(
    "Main.scala",
    "object Main {\n  def foo = \"\"\n  fo\n}\n",
    "  fo@@",
  )

  def beforeAll(): Unit = ()

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def complete(): CompletionList = {
    val pc = presentationCompiler(scalaVersion)
    request.complete(pc)
  }

}
