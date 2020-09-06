package kamon.catseffect
import cats.effect._
import cats.implicits._
import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.context.Context
import kamon.trace.{Span, SpanBuilder}

object KamonCatsEffectUtils {

  def markSpan[F[_], E, A](operationName: String)(io: F[A])(implicit F: Sync[F]): F[A] =
    markSpanCustom(F.delay {
      Kamon.spanBuilder(operationName)
    })(io)

  def markSpanCustom[F[_], E, A](
    createSpanBuilder: F[SpanBuilder],
  )(io: F[A])(implicit F: Sync[F]): F[A] = {
    createSpanBuilder
      .map { builder =>
        val newSpan = builder.start()
        val nextCtx: Context = Kamon.currentContext().withEntry(Span.Key, newSpan)
        val scope: Scope = Kamon.storeContext(nextCtx)
        (newSpan, scope)
      }
      .flatMap {
        case (span, scope) =>
          F.guaranteeCase(io) {
            case ExitCase.Completed =>
              F.delay {
                span.finish()
                scope.close()
              }
            case ExitCase.Canceled =>
              F.delay {
                span.finish()
                scope.close()
              }
            case ExitCase.Error(e) =>
              F.delay {
                span.fail(e)
                span.finish()
                scope.close()
              }
          }
      }
  }
}
