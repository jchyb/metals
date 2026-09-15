package bench

/**
 * Checks whether Scala3PcCompletionBench.complete's per-call cost is still
 * declining well past JMH's default 5-warmup-iteration window, or whether it
 * has genuinely settled at ~2x Scala2CompletionBench's cost. JMH's own
 * -f3 -wi5 -i20 run showed Scala3 at 20.09ms vs Scala2 at 8.69ms (steady per
 * JMH's own iteration boundaries) -- but a much longer (300-call) warmup in
 * ProfileScala3PcCompletion measured 8.965ms/op, suggesting JMH's 5-iteration
 * warmup may be too short for the Scala 3 PC path specifically. This prints
 * every call's wall time for both sides across many more iterations to see
 * the real convergence curve.
 *
 * Run with: bench/runMain bench.DebugWarmupCurve
 */
object DebugWarmupCurve {
  def timeIt(label: String, n: Int)(f: () => Unit): Unit = {
    println(s"--- $label ---")
    val times = new Array[Long](n)
    for (i <- 0 until n) {
      val start = System.nanoTime()
      f()
      times(i) = (System.nanoTime() - start) / 1000000
    }
    // print every call for the first 60, then every 20th afterwards
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

    val b2 = new Scala2CompletionBench()
    b2.setup()
    timeIt("scala2", n)(() => b2.complete())
    b2.tearDown()

    val b3 = new Scala3PcCompletionBench()
    b3.setup()
    timeIt("scala3", n)(() => b3.complete())
    b3.tearDown()
  }
}
