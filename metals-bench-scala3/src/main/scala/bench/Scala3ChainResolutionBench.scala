package bench

import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit

import scala.compiletime.uninitialized

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interactive.CachedLogicalPackage
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.interactive.LogicalPackagesProvider

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

/**
 * Tests cross-references BETWEEN sourcepath-only files -- unlike
 * Scala3SymbolResolutionBench, where every target is fully independent.
 * Here, resolving one class (via a fully-qualified extends reference, to
 * avoid reintroducing the import-specific confound already investigated)
 * forces the compiler to cascade through `depth` ancestors, each only
 * known via the sourcepath and each triggering its own
 * SourcefileLoader/lateCompile. Compares against the flat, non-chained
 * per-symbol cost already measured in Scala3SymbolResolutionBench to see
 * whether the cascade scales linearly (depth x per-symbol cost) or worse.
 */
@State(Scope.Benchmark)
class Scala3ChainResolutionBench {

  private val chainCount = 60
  private val depth = 20

  private var driver: InteractiveDriver = uninitialized
  private var leaves: IndexedSeq[String] = uninitialized
  private var nextIndex = 0

  @Setup(Level.Trial)
  def setup(): Unit =
    val dir = Files.createTempDirectory("scala3-chain-bench")
    leaves = ChainCorpus.generate(dir, chainCount, depth)

    val classpath = sys.props("java.class.path")
    val sourcePath = dir.toString
    def extractor(using Context) = Some(new LogicalPackagesProvider(sourcePath).root)
    driver = new InteractiveDriver(
      List("-color:never", "-classpath", classpath, "-sourcepath", sourcePath, "-Ylogical-package-loading"),
      CachedLogicalPackage(extractor),
    )

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def resolveChainLeaf(bh: Blackhole): Unit =
    val i = nextIndex
    nextIndex += 1
    val leafFqcn = leaves(i % leaves.length)
    val uri = URI.create(s"file:///Importer$i.scala")
    val diags = driver.run(uri, s"class Importer$i extends $leafFqcn")
    bh.consume(diags)

}
