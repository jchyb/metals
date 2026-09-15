package bench

import java.nio.file.Files

import one.convert.Arguments
import one.convert.JfrToFlame
import one.profiler.AsyncProfiler

/**
 * CPU-sampling profile of Scala3PcCompletionBench.complete -- the real
 * production PresentationCompiler.complete path for Scala 3
 * (dotty.tools.pc.ScalaPresentationCompiler), which JMH showed is ~2-2.3x
 * slower than the equivalent Scala 2 MetalsGlobal path
 * (Scala2CompletionBench), reproducibly across 3 JVM forks. This profile
 * looks at where that time actually goes.
 *
 * Run with: bench/runMain bench.ProfileScala3PcCompletion
 */
object ProfileScala3PcCompletion {
  def main(args: Array[String]): Unit = {
    val warmupCount = 700
    val profiledCount = 400

    val b = new Scala3PcCompletionBench()
    b.setup()

    for (_ <- 0 until warmupCount) b.complete()

    val jfrPath = Files.createTempFile("scala3-pc-completion-profile", ".jfr")
    val profiler = AsyncProfiler.getInstance()
    val startResult =
      profiler.execute(s"start,event=cpu,interval=1ms,jfr,file=$jfrPath")
    println(s"profiler start: $startResult")

    val start = System.nanoTime()
    for (_ <- 0 until profiledCount) b.complete()
    val elapsedMs = (System.nanoTime() - start) / 1000000
    println(
      s"completed $profiledCount requests in ${elapsedMs}ms (${elapsedMs.toDouble / profiledCount}ms/op)"
    )

    val stopResult = profiler.execute(s"stop,file=$jfrPath")
    println(s"profiler stop: $stopResult")

    val collapsedPath =
      Files.createTempFile("scala3-pc-completion-profile", ".collapsed.txt")
    val convArgs = new Arguments()
    convArgs.output = "collapsed"
    JfrToFlame.convert(jfrPath.toString, collapsedPath.toString, convArgs)

    val lines = Files.readAllLines(collapsedPath).iterator()
    val leafCounts =
      scala.collection.mutable.Map.empty[String, Long].withDefaultValue(0L)
    while (lines.hasNext) {
      val line = lines.next()
      val lastSpace = line.lastIndexOf(' ')
      if (lastSpace > 0) {
        val stack = line.substring(0, lastSpace)
        val count = line.substring(lastSpace + 1).toLongOption.getOrElse(0L)
        val leaf = stack.split(';').last
        leafCounts(leaf) += count
      }
    }

    println("\nTop 30 hot leaf frames (self-time samples):")
    leafCounts.toSeq.sortBy(-_._2).take(30).foreach { case (frame, count) =>
      println(f"$count%8d  $frame")
    }

    println(s"\njfr: $jfrPath")
    println(s"collapsed: $collapsedPath")

    b.tearDown()
  }
}
