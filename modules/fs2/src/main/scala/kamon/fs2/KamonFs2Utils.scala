package kamon.fs2
import cats.effect.{ExitCase, Sync}
import _root_.fs2.Stream
import cats.implicits._
import kamon.Kamon
import kamon.trace.SpanBuilder
import kamon.catseffect.KamonCatsEffectUtils.startSpanAndSetAsCurrentKamonContext

object KamonFs2Utils {
  implicit class Fs2StreamKamonExtensions[F[_], A](s: Stream[F, A]) {

    /**
      * Create a root span with a new trace ID. When the stream finished (successfully or with error)
      * the trace context is removed from the Kamon context.
      * This is commonly used for background tasks where there is no existing trace (unlike web requests)
      * but we want to create a new one to trace our internal and remote service calls.
      *
      * @param operationName Operation name of the root Span
      */
    def withNewTracePerElement(operationName: String)(implicit
      F: Sync[F],
    ): Stream[F, A] =
      withNewTracePerElementCustom(_ =>
        F.delay {
          Kamon.spanBuilder(operationName)
        },
      )

    def withNewTracePerElementCustom(
      mkSpanBuilder: A => F[SpanBuilder],
    )(implicit F: Sync[F]): Stream[F, A] =
      s.flatTap(ele =>
        Stream.bracketCase(mkSpanBuilder(ele).flatMap { b =>
          startSpanAndSetAsCurrentKamonContext(b.ignoreParentFromContext())
        }) {
          case ((span, scope), ExitCase.Completed) =>
            F.delay {
              span.finish()
              scope.close()
            }
          case ((span, scope), ExitCase.Canceled) =>
            F.delay {
              span.finish()
              scope.close()
            }
          case ((span, scope), ExitCase.Error(e)) =>
            F.delay {
              span.fail(e)
              span.finish()
              scope.close()
            }
        },
      )
  }
}
