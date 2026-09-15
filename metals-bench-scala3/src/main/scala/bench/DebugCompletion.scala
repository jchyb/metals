package bench

import java.net.URI

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interactive.CachedLogicalPackage
import dotty.tools.dotc.interactive.Completion
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.util.SourcePosition
import dotty.tools.dotc.util.Spans

/**
 * Sanity check for Scala3CompletionBench: prints what `Completion.completions`
 * actually returns, since the JMH numbers (~0.007ms) look suspiciously fast
 * for real completion computation.
 *
 * Run with: benchSourcepathScala3/runMain bench.DebugCompletion
 */
object DebugCompletion:
  def main(args: Array[String]): Unit =
    val code = "object Main {\n  def foo = \"\"\n  fo\n}\n"
    val offset = code.indexOf("  fo\n") + "  fo".length
    println(s"offset=$offset, char at offset-1='${code(offset - 1)}'")
    val uri = URI.create("file:///Main.scala")

    val classpath = sys.props("java.class.path")
    val driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath),
      CachedLogicalPackage.none,
    )
    val diags = driver.run(uri, code)
    println(s"diagnostics after run: ${diags.size}")
    diags.foreach(d => println(s"  ${d.message}"))

    given ctx: Context = driver.currentCtx
    val unit = driver.compilationUnits(uri)
    given newCtx: Context = ctx.fresh.setCompilationUnit(unit).setPhase(ctx.base.typerPhase)
    val pos = SourcePosition(unit.source, Spans.Span(offset))
    val (n, completions) = Completion.completions(pos)(using newCtx)
    println(s"completion count: ${completions.size} (n=$n)")
    completions.take(20).foreach(c => println(s"  ${c.label}"))
