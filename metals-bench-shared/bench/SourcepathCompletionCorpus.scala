package bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates one richer target class per package, for
 * Scala2SourcepathCompletionBench/Scala3PcSourcepathCompletionBench --
 * unlike SymbolResolutionCorpus's minimal 3-method class (shared with
 * several unrelated symbol-resolution benchmarks, so not modified here),
 * this target has a base trait, a case class field, overloads, generics,
 * an implicit parameter, currying, and a companion object -- closer to
 * what a real class actually looks like, so member completion has more
 * (and more varied) candidates to enumerate/rank than a trivial 3-method
 * class would.
 *
 * Each package is entirely self-contained (base trait + helper case class +
 * target class + companion, all in one file), one per package so every
 * benchmark invocation forces a fresh, never-before-touched sourcepath
 * compile, matching the same "first reference this session" rationale as
 * SymbolResolutionCorpus/ChainCorpus.
 *
 * Returns the fully-qualified package name (not the class name -- callers
 * import the whole package, matching how the real
 * FallbackSourcepathCrossFileLspSuite test references sourcepath-only
 * symbols) for each generated target, in generation order.
 */
object SourcepathCompletionCorpus {

  def generate(root: Path, count: Int): IndexedSeq[String] =
    for (i <- 0 until count) yield {
      val pkgName = s"sourcepathcompletioncorpus.target$i"
      val pkgDir = root.resolve(pkgName.replace('.', '/'))
      Files.createDirectories(pkgDir)
      val content =
        s"""package $pkgName
           |
           |trait Base$i {
           |  def baseMethod(x: Int): Int
           |}
           |
           |case class Helper$i(value: String, count: Int) {
           |  def describe: String = s"$$value:$$count"
           |}
           |
           |class Target$i(val name: String, val items: List[Int]) extends Base$i {
           |  def baseMethod(x: Int): Int = x + items.size
           |
           |  def method0(a: Int): Int = a + $i
           |  def method1(a: Int, b: String = "default"): Int = a * 2 - $i + b.length
           |  def method2(a: Int): Int = if (a > 0) method0(a) else method1(a)
           |  def method3[T](x: T): List[T] = List(x, x)
           |  def method4(implicit ord: Ordering[Int]): Int = items.sorted(ord).headOption.getOrElse($i)
           |
           |  def curried(a: Int)(b: Int): Int = a + b + $i
           |
           |  val field0: String = name.reverse
           |  val field1: Helper$i = Helper$i(name, items.size)
           |
           |  override def toString: String = s"Target$i($$name)"
           |}
           |
           |object Target$i {
           |  def apply(name: String): Target$i = new Target$i(name, List($i, $i + 1, $i + 2))
           |  val default: Target$i = apply("default-$i")
           |}
           |""".stripMargin
      Files.write(
        pkgDir.resolve(s"Target$i.scala"),
        content.getBytes(StandardCharsets.UTF_8),
      )
      pkgName
    }

  /**
   * The requesting file's content for Scala2SourcepathCompletionBench /
   * Scala3PcSourcepathCompletionBench, given the index/package produced by
   * `generate`. Factored out here (rather than duplicated in both benchmark
   * files) so the two stay byte-for-byte identical and the escaping (every
   * `$` that's genuine interpolation IN THE GENERATED FILE, as opposed to a
   * substitution from `i`/`pkg` here, needs to be written as `$$`) only has
   * to be gotten right once.
   *
   * Deliberately more than a single completion call: a small ADT (Shape) with
   * pattern-matching + string interpolation, a custom exception, a generic
   * repository trait + implementation, Either-based validation, Future,
   * Try, a for-comprehension, and multiple methods calling each other --
   * closer to a real file a user would actually have open, so typechecking
   * it costs meaningfully more than a 3-line snippet. The one completion
   * point (`tgt.method@@`, at the very end, the only place that exact
   * substring appears) is unchanged in spirit from the smaller version.
   */
  def mainCode(i: Int, pkg: String): String =
    s"""|import scala.annotation.tailrec
        |import scala.collection.mutable
        |import scala.util.Try
        |import scala.concurrent.Future
        |import scala.concurrent.ExecutionContext.Implicits.global
        |import $pkg._
        |
        |trait Shape
        |case class Circle(radius: Double) extends Shape
        |case class Rectangle(width: Double, height: Double) extends Shape
        |case class Triangle(base: Double, height: Double) extends Shape
        |
        |object ShapeOps {
        |  def area(shape: Shape): Double = shape match {
        |    case Circle(r) => math.Pi * r * r
        |    case Rectangle(w, h) => w * h
        |    case Triangle(b, h) => 0.5 * b * h
        |    case _ => 0.0
        |  }
        |
        |  def describe(shape: Shape): String = {
        |    val a = area(shape)
        |    shape match {
        |      case Circle(r) => s"Circle(r=$$r, area=$$a)"
        |      case Rectangle(w, h) => s"Rectangle($$w x $$h, area=$$a)"
        |      case Triangle(b, h) => s"Triangle(base=$$b, height=$$h, area=$$a)"
        |      case _ => "Unknown"
        |    }
        |  }
        |}
        |
        |case class ValidationError(message: String) extends Exception(message)
        |
        |trait Repository[K, V] {
        |  def get(key: K): Option[V]
        |  def put(key: K, value: V): Unit
        |  def all: List[V]
        |}
        |
        |class InMemoryRepository[K, V] extends Repository[K, V] {
        |  private var storage: Map[K, V] = Map.empty
        |  def get(key: K): Option[V] = storage.get(key)
        |  def put(key: K, value: V): Unit = storage += key -> value
        |  def all: List[V] = storage.values.toList
        |}
        |
        |trait Show[T] {
        |  def show(t: T): String
        |}
        |
        |object Show {
        |  implicit val showInt: Show[Int] = (t: Int) => s"Int($$t)"
        |  implicit val showString: Show[String] = (t: String) => s"Str($$t)"
        |  implicit val showShape: Show[Shape] = (t: Shape) => ShapeOps.describe(t)
        |
        |  def show[T](t: T)(implicit s: Show[T]): String = s.show(t)
        |}
        |
        |trait Logger {
        |  def log(msg: String): Unit
        |}
        |
        |class ConsoleLogger extends Logger {
        |  def log(msg: String): Unit = println(s"[console] $$msg")
        |}
        |
        |class NullLogger extends Logger {
        |  def log(msg: String): Unit = ()
        |}
        |
        |class Cache[K, V] {
        |  private val storage = mutable.Map.empty[K, V]
        |  def getOrCompute(key: K)(compute: K => V): V =
        |    storage.getOrElseUpdate(key, compute(key))
        |  def size: Int = storage.size
        |}
        |
        |object Main$i {
        |
        |  private val helper: Helper$i = Helper$i("seed-$i", $i)
        |  private val repo: Repository[Int, Target$i] = new InMemoryRepository[Int, Target$i]
        |  private val logger: Logger = new ConsoleLogger
        |  private val fibCache: Cache[Int, Long] = new Cache[Int, Long]
        |
        |  def validate(x: Int): Either[ValidationError, Int] =
        |    if (x >= 0) Right(x) else Left(ValidationError(s"negative value: $$x"))
        |
        |  def process(items: List[Int]): List[Int] =
        |    items.flatMap { x =>
        |      validate(x) match {
        |        case Right(v) =>
        |          val target: Target$i = Target$i.default
        |          repo.put(v, target)
        |          Some(target.method0(v) + helper.count)
        |        case Left(_) =>
        |          None
        |      }
        |    }
        |
        |  def asyncCompute(x: Int): Future[Int] = Future {
        |    val target = Target$i.default
        |    target.curried(x)(helper.count)
        |  }
        |
        |  def tryParse(s: String): Try[Int] = Try(s.toInt)
        |
        |  def shapesSummary(shapes: List[Shape]): String =
        |    shapes.map(ShapeOps.describe).mkString(", ")
        |
        |  @tailrec
        |  def fibonacciIter(n: Int, a: Long = 0, b: Long = 1): Long =
        |    if (n == 0) a else fibonacciIter(n - 1, b, a + b)
        |
        |  def memoizedFibonacci(n: Int): Long =
        |    fibCache.getOrCompute(n) { k => fibonacciIter(k) }
        |
        |  def describeAll[T](items: List[T])(implicit s: Show[T]): List[String] =
        |    items.map(Show.show(_))
        |
        |  def sumWith(items: List[Int])(f: Int => Int): Int =
        |    items.foldLeft(0) { (acc, x) => acc + f(x) }
        |
        |  def run(): Unit = {
        |    val shapes = List(Circle(1.0), Rectangle(2.0, 3.0), Triangle(4.0, 5.0))
        |    println(shapesSummary(shapes))
        |    logger.log(describeAll(shapes).mkString(", "))
        |
        |    val results = process(List(1, 2, 3, -1, 4))
        |    println(results)
        |
        |    val validated = for {
        |      parsed <- tryParse("42").toOption
        |      v <- validate(parsed).toOption
        |    } yield v
        |    println(validated)
        |
        |    val fibs = (0 until 10).map(memoizedFibonacci)
        |    logger.log(s"fibs=$$fibs cacheSize=$${fibCache.size}")
        |
        |    val total = sumWith(List(1, 2, 3, 4, 5))(x => x * x)
        |    logger.log(describeAll(List(total, results.sum)).mkString(" | "))
        |
        |    val tgt = Target$i.default
        |    tgt.method
        |  }
        |}
        |""".stripMargin
}
