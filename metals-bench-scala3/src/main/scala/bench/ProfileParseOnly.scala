package bench

import java.nio.file.Files

import scala.io.Codec
import scala.jdk.CollectionConverters.*

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interactive.CachedLogicalPackage
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.parsing.Parsers
import dotty.tools.dotc.util.SourceFile
import dotty.tools.io.AbstractFile

import one.convert.Arguments
import one.convert.JfrToFlame
import one.profiler.AsyncProfiler

/**
 * Ad-hoc CPU-sampling profile of the same `OutlineParser.parse()` loop
 * benchmarked in Scala3SourcepathPhaseBench.parseOnly, to see which method(s)
 * inside dotc's parser/scanner dominate self-time. Neither `-no-indent` nor
 * `-Xdrop-comments` explained the ~6.8x gap against mtags' equivalent (see
 * Scala2SourcepathBench), so this looks for the actual hot path directly
 * instead of guessing more toggles.
 *
 * Run with: benchSourcepathScala3/runMain bench.ProfileParseOnly
 */
object ProfileParseOnly:

  def main(args: Array[String]): Unit =
    val packages = 50
    val filesPerPackage = 20
    val methodsPerFile = 10
    val repeats = 200 // ~200 * 67ms ~= 13s of sampling, plenty for a stable profile

    val dir = Files.createTempDirectory("scala3-sourcepath-profile")
    SourcepathCorpus.generate(dir, packages, filesPerPackage, methodsPerFile)

    val classpath = sys.props("java.class.path")
    val driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath, "-sourcepath", dir.toString, "-Ylogical-package-loading"),
      CachedLogicalPackage.none,
    )
    given ctx: Context = driver.currentCtx

    val files = Files
      .walk(dir)
      .iterator()
      .asScala
      .filter(p => p.toString.endsWith(".scala"))
      .map(p => AbstractFile.getFile(p.toString))
      .toList

    // Read every file once, up front. Reusing these SourceFile instances
    // across repeats avoids re-hitting disk on every iteration (which
    // dominated an earlier, unfixed version of this profile: ~80% of
    // samples were in __open/read/close, not the parser at all).
    val sourceFiles = files.map { f =>
      val sf = SourceFile(f, ctx.settings.sourceroot.value, Codec(ctx.settings.encoding.value))
      sf.content()
      sf
    }

    def parseAll(): Int =
      var count = 0
      for sf <- sourceFiles do
        val parser = new Parsers.OutlineParser(sf)
        count += parser.parse().hashCode()
      count

    // Warm up the JIT before profiling so we're sampling steady-state code.
    for _ <- 0 until 20 do parseAll()

    val jfrPath = Files.createTempFile("scala3-sourcepath-profile", ".jfr")
    val profiler = AsyncProfiler.getInstance()
    val startResult = profiler.execute(s"start,event=cpu,interval=1ms,jfr,file=$jfrPath")
    println(s"profiler start: $startResult")

    val start = System.nanoTime()
    var sink = 0
    for _ <- 0 until repeats do sink += parseAll()
    val elapsedMs = (System.nanoTime() - start) / 1000000
    println(s"parsed $repeats x ${files.size} files in ${elapsedMs}ms (sink=$sink)")

    val stopResult = profiler.execute(s"stop,file=$jfrPath")
    println(s"profiler stop: $stopResult")

    val collapsedPath = Files.createTempFile("scala3-sourcepath-profile", ".collapsed.txt")
    val convArgs = new Arguments()
    convArgs.output = "collapsed"
    JfrToFlame.convert(jfrPath.toString, collapsedPath.toString, convArgs)

    val lines = Files.readAllLines(collapsedPath).asScala
    val leafCounts = scala.collection.mutable.Map.empty[String, Long].withDefaultValue(0L)
    for line <- lines do
      val lastSpace = line.lastIndexOf(' ')
      if lastSpace > 0 then
        val stack = line.substring(0, lastSpace)
        val count = line.substring(lastSpace + 1).toLongOption.getOrElse(0L)
        val leaf = stack.split(';').last
        leafCounts(leaf) += count

    println("\nTop 25 hot leaf frames (self-time samples):")
    leafCounts.toSeq.sortBy(-_._2).take(25).foreach { case (frame, count) =>
      println(f"$count%8d  $frame")
    }

    println(s"\njfr: $jfrPath")
    println(s"collapsed: $collapsedPath")
