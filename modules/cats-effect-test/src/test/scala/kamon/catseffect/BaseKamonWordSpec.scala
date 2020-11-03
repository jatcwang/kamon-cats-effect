package kamon.catseffect

import kamon.Kamon
import kamon.testkit.TestSpanReporter
import kamon.trace.Span
import org.scalactic.source.Position
import org.scalatest.{Assertion, BeforeAndAfterEach}
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers._

abstract class BaseKamonWordSpec
    extends AnyWordSpec
    with TestSpanReporter
    with BeforeAndAfterEach {

  override def afterEach(): Unit = {
    super.afterEach()
    testSpanReporter().clear()
  }

  def getAllReportedSpans(): Seq[Span.Finished] =
    // Give some time for spans to be flushed (kamon test kit flushes every 1ms)
    testSpanReporter().spans(10.millis)

  // Given a operation name, extract all spans with the same trace
  def extractSpansForTraceOf(spans: Seq[Span.Finished], opName: String): Seq[Span.Finished] = {
    val ss = spans.filter(_.operationName == opName)
    ss.length shouldBe 1
    val s = ss.head
    assert(!s.trace.id.isEmpty)

    spans.filter(_.trace.id == s.trace.id)
  }

  def drawSpanDiagram(allSpans: Seq[Span.Finished]): String = {

    def draw(indentation: Int, unsortedCurSpans: Seq[Span.Finished]): Seq[String] = {
      if (unsortedCurSpans.isEmpty) List.empty
      else {
        val curSpans = unsortedCurSpans.sortBy(_.from)
        curSpans
          .flatMap { s =>
            Seq(s"${"  " * indentation}${s.operationName}") ++ draw(
              indentation + 1,
              allSpans.filter(_.parentId == s.id),
            )
          }
      }
    }

    val rootSpans = allSpans.filter(_.parentId.isEmpty)

    draw(0, rootSpans).mkString("\n")
  }

  def assertSpanDiagram(
    allSpans: Seq[Span.Finished],
    expectedDiagram: String,
  ): Assertion = {
    val expectedStripped = expectedDiagram.stripMargin.strip
    drawSpanDiagram(allSpans) shouldBe expectedStripped
  }

  def assertCurrentOperationName(name: String)(implicit pos: Position): Assertion =
    Kamon.currentSpan().operationName() shouldBe name

  def getSpanWithName(spans: Seq[Span.Finished], name: String): Span.Finished = {
    val s = spans.filter(_.operationName == name)
    s.length shouldBe 1
    s.head
  }

}
