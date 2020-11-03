package kamon.catseffect

import java.util.concurrent.{ConcurrentHashMap, Executors}

import cats.effect.IO
import kamon.Kamon
import kamon.catseffect.KamonCatsEffectUtils.{markSpan, markSpanCustom}
import kamon.tag.Tag.unwrapValue
import kamon.testkit.TestSpanReporter
import kamon.trace.{Identifier, Span}
import org.scalactic.source.Position
import org.scalatest.{Assertion, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class KamonCatsEffectUtilsSpec extends BaseKamonWordSpec {
  val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))
  implicit val cs = IO.contextShift(ec)

  "markSpan" should {

    "BUG: IO.shift seems store add another layer of context, causing next spans to be children instead of siblings" in {
      cs.evalOn(ec)(markSpan("root")(for {
        _ <- markSpan("1")(IO {
          assertCurrentOperationName("1")
        })
        _ <- markSpan("2")(
          for {
            _ <- IO.shift
            _ <- markSpan("21")(IO.unit)
          } yield (),
        )
        _ <- markSpan("3")(IO.unit)
      } yield succeed))
        .unsafeRunSync()

      val allSpans = getAllReportedSpans()
      assertSpanDiagram(allSpans,
        """
          |root
          |  1
          |  2
          |    21
          |    3
          |""".stripMargin)

      /*
      It really should be
      |root
      |  1
      |  2
      |    21
      |  3
      */
    }

    "Creates a new span for the provided IO operation (with correct parent span at the execution point)" in {
      // Add in some manual IO.shifts to ensure instrumentation works across async boundaries

      cs.evalOn(ec)(markSpan("root")(for {
        _ <- markSpan("1")(IO {
          assertCurrentOperationName("1")
        })
//        _ <- IO.shift
        _ <- markSpan("2")(
          for {
//            _ <- IO.shift
            _ <- IO {
              assertCurrentOperationName("2")
            }
//            _ <- IO.shift
            _ <- markSpan("21")(IO {
              assertCurrentOperationName("21")
            })
//            _ <- IO.shift
            _ <- markSpan("22")(IO {
              assertCurrentOperationName("22")
            })
          } yield (),
        )
//        _ <- IO.shift
        _ <- markSpan("3")(IO {
          assertCurrentOperationName("3")
        })
      } yield succeed))
        .unsafeRunSync()

      val allSpans = getAllReportedSpans()

      val traceIds = allSpans.map(_.trace.id.string).distinct
      traceIds.length == 1
      assert(traceIds.head.nonEmpty)

      println(drawSpanDiagram(allSpans))

      assertSpanDiagram(allSpans, """
        |root
        |  1
        |  2
        |    21
        |    22
        |  3
      """)
    }

    "is referentially transparent" in {
      val op = markSpan("op")(IO {
        assertCurrentOperationName("op")
      })

      markSpan("root")(for {
        _ <- markSpan("1")(for {
          _ <- IO(assertCurrentOperationName("1"))
          _ <- op
        } yield ())
        _ <- markSpan("2")(for {
          _ <- IO(assertCurrentOperationName("2"))
          _ <- op
        } yield ())
      } yield succeed).unsafeRunSync()

      assertSpanDiagram(getAllReportedSpans(), """
        |root
        |  1
        |    op
        |  2
        |    op
        |""")
    }

    "mark the span as failed if an exception was thrown in the IO" in {
      markSpan("root")(for {
        _ <- markSpan("1")(IO {
          assertCurrentOperationName("1")
        })
        _ <- markSpan("2")(
          for {
            _ <- IO {
              assertCurrentOperationName("2")
            }
            _ <- markSpan("21")(for {
              _ <- IO(assertCurrentOperationName("21"))
              _ <- IO.raiseError[Unit](new Exception("oops"))
            } yield ()).attempt
            _ <- markSpan("22")(IO {
              assertCurrentOperationName("22")
            })
          } yield (),
        )
        _ <- markSpan("3")(IO {
          assertCurrentOperationName("3")
        })
      } yield succeed).unsafeRunSync()

      val allSpans = getAllReportedSpans()

      assert(getSpanWithName(allSpans, "21").hasError)

      assertSpanDiagram(allSpans, """
          |root
          |  1
          |  2
          |    21
          |    22
          |  3
        """)
    }
  }

  "markSpanCustom" should {
    "Setup the span using the provided IO[SpanBuilder]" in {
      val io11 = markSpanCustom(IO {
        val b = Kamon.spanBuilder("11")
        b.tag("custom_value", 100)
      })(IO {
        assertCurrentOperationName("11")
      })

      val ioNewTrace = markSpanCustom(IO {
        val b = Kamon.spanBuilder("disowned")
        b.ignoreParentFromContext()
      })(IO {
        assertCurrentOperationName("disowned")
      })

      var span1: Option[Span] = None

      markSpan("root")(for {
        _ <- markSpan("1")(for {
          _ <- IO {
            assertCurrentOperationName("1")
            span1 = Some(Kamon.currentSpan())
          }
          _ <- io11
        } yield ())
        _ <- markSpan("2")({
          for {
            _ <- IO(assertCurrentOperationName("2"))
            _ <- ioNewTrace
            _ <- markSpanCustom(IO {
              val s = Kamon.spanBuilder("1_custom_child")
              s.asChildOf(span1.get)
              s
            })(IO(assertCurrentOperationName("1_custom_child")))
          } yield ()
        })
      } yield ()).unsafeRunSync()

      val allSpans = getAllReportedSpans()

      val span11 = getSpanWithName(allSpans, "11")

      span11.tags.all().map(t => t.key -> unwrapValue(t)) shouldBe Seq("custom_value" -> 100)

      val disownedSpan = getSpanWithName(allSpans, "disowned")
      assert(disownedSpan.parentId.isEmpty)

      val traceIdForAllSpanOtherThanDisowned = {
        val ss = allSpans.filter(_.operationName != "disowned").map(_.trace.id).distinct
        ss.length shouldBe 1
        ss.head
      }

      disownedSpan.trace.id shouldNot be(traceIdForAllSpanOtherThanDisowned)

      assertSpanDiagram(allSpans, """
          |root
          |  1
          |    11
          |    1_custom_child
          |  2
          |disowned
        """)
    }
  }

  // fixme high contention test
}

object KamonCatsEffectUtilsSpec {
  class SpanRecorder {
    private val spans: scala.collection.concurrent.Map[String, SpanInfo] =
      new ConcurrentHashMap[String, SpanInfo]().asScala

    def checkAndAddCurrentSpan(expectedOpName: String)(implicit pos: Position): Unit = {
      val curSpan = Kamon.currentSpan()
      curSpan.operationName() shouldBe expectedOpName
      spans.get(expectedOpName) match {
        case Some(existingSpan) => toSpanInfo(curSpan) shouldBe existingSpan
        case None               => spans.put(expectedOpName, toSpanInfo(curSpan))
      }
    }

    def getOpNameBySpanId(id: Identifier): List[String] =
      spans.collect {
        case (opName, spanInfo) if spanInfo.id == id => opName
      }.toList

    def assertParentChild(parentOpName: String, childOpName: String): Assertion = {
      val expectedParent =
        spans.get(parentOpName).getOrElse(fail(s"'$parentOpName' span not found"))
      val c = spans.get(childOpName).getOrElse(fail(s"'$childOpName' span not found"))

      c.parentIdOpt match {
        case None => fail(s"span '$childOpName' doesn't have parent")
        case Some(parentId) =>
          assert(
            parentId == expectedParent.id,
            s"expected parent is '$parentOpName' but actual parent is ${getOpNameBySpanId(parentId)}",
          )
      }
    }

    def assertNoParent(spanOpName: String): Assertion = {
      val c = spans.get(spanOpName).getOrElse(fail(s"'$spanOpName' span not found"))
      c.parentIdOpt match {
        case None => succeed
        case Some(parentId) =>
          fail(s"expected no parent for '$spanOpName' but got '${getOpNameBySpanId(parentId)}'")
      }
    }
  }

  final case class SpanInfo(id: Identifier, parentIdOpt: Option[Identifier], traceId: Identifier)

  def toSpanInfo(span: Span)(implicit pos: Position): SpanInfo = {
    assert(!span.id.isEmpty, "Span id is empty")
    assert(!span.trace.id.isEmpty, "Span's trace id is empty")
    val parId = if (span.parentId.isEmpty) None else Some(span.parentId)
    SpanInfo(span.id, parId, span.trace.id)
  }

}
