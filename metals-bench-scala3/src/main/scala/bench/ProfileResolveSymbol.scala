package bench

import java.net.URI
import java.nio.file.Files

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interactive.CachedLogicalPackage
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.interactive.LogicalPackagesProvider

import one.convert.Arguments
import one.convert.JfrToFlame
import one.profiler.AsyncProfiler

/**
 * Ad-hoc CPU-sampling profile of
 * Scala3SymbolResolutionBench.resolveOneSymbolViaImport -- JMH isolated this
 * to specifically be the expensive shape: `import $fqcn` followed by using
 * the imported SIMPLE name (~20.48ms marginal cost), whereas a
 * fully-qualified reference to the exact same symbol (`resolveOneSymbol`,
 * no import) costs ~0.17ms, and an unused import alone costs ~0.54ms. So the
 * cost is specifically in resolving a simple name against an import of a
 * not-yet-typechecked sourcepath-only symbol -- this profile looks at what
 * that resolution actually does.
 *
 * warmupCount is much higher than the first pass at this (50) since that
 * profile was heavily polluted by HotSpot's own C2 background JIT compiler
 * threads (PhaseChaitin/PhaseIdealLoop/etc. in the leaf-frame list) -- not
 * yet JIT-settled after only 50 iterations.
 *
 * Run with: benchSourcepathScala3/runMain bench.ProfileResolveSymbol
 */
object ProfileResolveSymbol:

  def main(args: Array[String]): Unit =
    val warmupCount = 300
    val profiledCount = 300
    val totalTargets = warmupCount + profiledCount + 10 // small safety margin

    val dir = Files.createTempDirectory("scala3-symres-profile")
    val targets = SymbolResolutionCorpus.generate(dir, totalTargets)

    val classpath = sys.props("java.class.path")
    val sourcePath = dir.toString
    def extractor(using Context) = Some(new LogicalPackagesProvider(sourcePath).root)
    val driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath, "-sourcepath", sourcePath, "-Ylogical-package-loading"),
      CachedLogicalPackage(extractor),
    )

    var nextIndex = 0
    def resolveNext(): Unit =
      val i = nextIndex
      nextIndex += 1
      val fqcn = targets(i)
      val simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1)
      val uri = URI.create(s"file:///Importer$i.scala")
      driver.run(uri, s"import $fqcn\nclass Importer$i extends $simpleName")

    // Warm up the JIT -- each call still does a real resolve+typecheck of a
    // fresh target (a repeat would just be a cache hit), so this genuinely
    // exercises the typer/namer code paths before we start sampling.
    for _ <- 0 until warmupCount do resolveNext()

    val jfrPath = Files.createTempFile("scala3-symres-profile", ".jfr")
    val profiler = AsyncProfiler.getInstance()
    val startResult = profiler.execute(s"start,event=cpu,interval=1ms,jfr,file=$jfrPath")
    println(s"profiler start: $startResult")

    val start = System.nanoTime()
    for _ <- 0 until profiledCount do resolveNext()
    val elapsedMs = (System.nanoTime() - start) / 1000000
    println(s"resolved $profiledCount symbols in ${elapsedMs}ms (${elapsedMs.toDouble / profiledCount}ms/op)")

    val stopResult = profiler.execute(s"stop,file=$jfrPath")
    println(s"profiler stop: $stopResult")

    val collapsedPath = Files.createTempFile("scala3-symres-profile", ".collapsed.txt")
    val convArgs = new Arguments()
    convArgs.output = "collapsed"
    JfrToFlame.convert(jfrPath.toString, collapsedPath.toString, convArgs)

    val lines = Files.readAllLines(collapsedPath).iterator()
    val leafCounts = scala.collection.mutable.Map.empty[String, Long].withDefaultValue(0L)
    while lines.hasNext do
      val line = lines.next()
      val lastSpace = line.lastIndexOf(' ')
      if lastSpace > 0 then
        val stack = line.substring(0, lastSpace)
        val count = line.substring(lastSpace + 1).toLongOption.getOrElse(0L)
        val leaf = stack.split(';').last
        leafCounts(leaf) += count

    println("\nTop 30 hot leaf frames (self-time samples):")
    leafCounts.toSeq.sortBy(-_._2).take(30).foreach { case (frame, count) =>
      println(f"$count%8d  $frame")
    }

    println(s"\njfr: $jfrPath")
    println(s"collapsed: $collapsedPath")
