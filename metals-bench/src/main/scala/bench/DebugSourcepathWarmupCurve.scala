package bench

/**
 * Same long-run warmup-curve check as DebugWarmupCurve, but for the
 * sourcepath-completion-candidate scenario: JMH's default -wi5 -i20 window
 * showed Scala3PcSourcepathCompletionBench at ~1.76x Scala2's cost, but the
 * plain-buffer completion benchmarks showed the SAME kind of short window
 * badly understating the true steady-state gap (2x -> 4x once fully
 * JIT-settled). Confirm whether that also happens here, and whether the
 * corpus (1000 targets) is large enough to avoid target-reuse cache hits
 * across the whole curve.
 *
 * Run with: bench/runMain bench.DebugSourcepathWarmupCurve
 */
object DebugSourcepathWarmupCurve {
  def timeIt(label: String, n: Int)(f: () => Unit): Unit = {
    println(s"--- $label ---")
    val times = new Array[Long](n)
    for (i <- 0 until n) {
      val start = System.nanoTime()
      f()
      times(i) = (System.nanoTime() - start) / 1000000
    }
    for (i <- 0 until n) {
      if (i < 20 || i % 50 == 0) println(f"  [$label] iter $i%4d: ${times(i)}%6d ms")
    }
    for (chunkStart <- Seq(n - 200, n - 100, n - 50) if chunkStart >= 0) {
      val chunk = times.slice(chunkStart, n)
      println(
        f"  [$label] mean of last ${n - chunkStart}%d: ${chunk.sum.toDouble / chunk.size}%.3f ms/op"
      )
    }
  }

  def main(args: Array[String]): Unit = {
    val n = 700

    val b2 = new Scala2SourcepathCompletionBench()
    b2.setup()
    timeIt("scala2-sourcepath", n)(() => b2.complete())
    b2.tearDown()

    val b3 = new Scala3PcSourcepathCompletionBench()
    b3.setup()
    timeIt("scala3-sourcepath", n)(() => b3.complete())
    b3.tearDown()
  }
}
