package bench

import scala.reflect.internal.util.BatchSourceFile

import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.interactive.Response
import scala.tools.nsc.reporters.ConsoleReporter

/**
 * Sanity check for Scala2CompletionBench: prints what `askTypeCompletion`
 * actually returns, since the JMH number (~0.007ms) looks suspiciously fast
 * for real completion computation.
 *
 * Run with: bench/runMain bench.DebugCompletion2
 */
object DebugCompletion2 {
  def main(args: Array[String]): Unit = {
    val rawCode = "object Main {\n  def foo = \"\"\n  fo\n}\n"
    val offset = rawCode.indexOf("  fo\n") + "  fo".length
    val code = rawCode.take(offset) + "_CURSOR_" + rawCode.drop(offset)
    println(s"offset=$offset, code with cursor:\n$code")

    val settings = new Settings(println)
    settings.usejavacp.value = true
    val g = new Global(settings, new ConsoleReporter(settings))
    val source = new BatchSourceFile("Main.scala", code)
    val reloadResponse = new Response[Unit]
    g.askReload(List(source), reloadResponse)
    val reloadResult = reloadResponse.get
    println(s"reload result: $reloadResult")

    val pos = source.position(offset)
    val response = new Response[List[g.Member]]
    g.askTypeCompletion(pos, response)
    val result = response.get
    result match {
      case Left(members) =>
        println(s"completion count: ${members.size}")
        members.take(20).foreach(m => println(s"  ${m.sym.nameString}"))
      case Right(err) =>
        println(s"completion FAILED: $err")
        err.printStackTrace()
    }
  }
}
