package bench

import java.nio.file.Files
import java.util.concurrent.TimeUnit

import scala.meta.pc.PresentationCompiler

import org.eclipse.lsp4j.CompletionList
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State


@State(Scope.Benchmark)
class Scala3PcSourcepathCompletionBench extends PcBenchmark {

  private val corpusSize = 1000
  private val scalaVersion = "3.10.1-RC1-bin-SNAPSHOT"

  private var targetPackages: IndexedSeq[String] = _
  private var pc: PresentationCompiler = _
  private var nextIndex = 0

  def beforeAll(): Unit = ()

  @Setup(Level.Trial)
  override def setup(): Unit = {
    val dir = Files.createTempDirectory("scala3-sourcepath-completion-bench")
    targetPackages = SourcepathCompletionCorpus.generate(dir, corpusSize)
    pc = presentationCompilers.getOrElseUpdate(
      s"$scalaVersion-sourcepath",
      newPC(scalaVersion, newSearch(), List(dir)),
    )
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def complete(): CompletionList = {
    val i = nextIndex
    nextIndex += 1
    val pkg = targetPackages(i % targetPackages.length)
    val request = SourceRequest.fromPath(
      s"Main$i.scala",
      SourcepathCompletionCorpus.mainCode(i, pkg),
      "tgt.method@@",
    )
    request.complete(pc)
  }

}
