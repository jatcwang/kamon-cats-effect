package kamon.fs2

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

import cats.implicits._
import kamon.testkit.TestSpanReporter
import kamon.trace.Span
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers._
import KamonFs2Utils._
import kamon.catseffect.KamonCatsEffectUtils._

import scala.concurrent.duration._
import kamon.catseffect.BaseKamonWordSpec
import _root_.fs2.Stream
import cats.effect.IO
import kamon.Kamon

import scala.concurrent.ExecutionContext

class KamonFs2UtilsSpec extends BaseKamonWordSpec {
  val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))
  implicit val cs = IO.contextShift(ec)

  "withNewTracePerElement" should {
    // FIXME: so the real reason why things fall apart in the face of concurrency is because
    // IO.bracket does not guarantee that the cleanup is executed immediately after "use" operation and on the same thread
    // which means we cannot guarantee that we'll get the same thread in our cleanup, which means
    // we may be setting/cleaning threadlocals on the wrong thread!!
    "start a new trace per element of the Stream" in {
      val s = Stream
        .emits(1.to(100))
        .covary[IO]
        .withNewTracePerElementCustom(idx =>
          IO {
            Kamon.spanBuilder(s"$idx.root")
          },
        )
        .parEvalMap(20)(idx =>
          markSpan(s"$idx.1")(for {
//            _ <- IO(assertCurrentOperationName(s"$idx.1"))
            _ <- markSpan(s"$idx.11")(IO.unit)
          } yield idx),
        )
        .evalMap(idx =>
          markSpan(s"$idx.2")(for {
//            _ <- IO(assertCurrentOperationName(s"$idx.2"))
            _ <- markSpan(s"$idx.21")(IO.unit)
          } yield ()),
        )

      cs.evalOn(ec)(
        s.compile.drain,
      ).unsafeRunSync()

      val allSpans = getAllReportedSpans()

      println(drawSpanDiagram(allSpans))

      (1 to 3).foreach { i =>
        assertSpanDiagram(extractSpansForTraceOf(allSpans, s"$i.root"), s"""
        |$i.root
        |  $i.1
        |    $i.11
        |  $i.2
        |    $i.21
        """)
      }
    }
  }
}
