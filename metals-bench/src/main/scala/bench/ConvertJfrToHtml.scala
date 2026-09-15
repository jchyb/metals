package bench

import one.convert.Arguments
import one.convert.JfrToFlame

/**
 * One-off: convert an already-captured .jfr file to an interactive HTML
 * flamegraph (instead of the leaf-frame-only collapsed text summary used by
 * ProfileScala3PcCompletion/ProfileScala2PcCompletion).
 *
 * Run with: bench/runMain bench.ConvertJfrToHtml <input.jfr> <output.html> [title]
 */
object ConvertJfrToHtml {
  def main(args: Array[String]): Unit = {
    val Array(input, output, title) = if (args.length >= 3) args.take(3) else Array(args(0), args(1), "flamegraph")
    val convArgs = new Arguments()
    convArgs.title = title
    if (output.endsWith(".collapsed.txt")) convArgs.output = "collapsed"
    JfrToFlame.convert(input, output, convArgs)
    println(s"wrote $output")
  }
}
