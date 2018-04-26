package utils

import javax.inject.{Inject, Singleton}

import com.impactua.bouncer.commons.models.ResponseCode
import com.impactua.bouncer.commons.web.ErrorHandler
import com.mohiva.play.silhouette.api.actions.{SecuredErrorHandler, UnsecuredErrorHandler}
import play.api.libs.json._
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}
import play.api.routing.Router
import play.api.{Configuration, Environment, OptionalSourceMapper}

import scala.compat.Platform
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServerErrorHandler @Inject()(env: Environment,
                                   config: Configuration,
                                   sourceMapper: OptionalSourceMapper,
                                   router: javax.inject.Provider[Router]
                                  )(implicit ec: ExecutionContext) extends ErrorHandler(env, config, sourceMapper, router)
  with SecuredErrorHandler with UnsecuredErrorHandler {

  /**
    * Called when a user is authenticated but not authorized.
    *
    * As defined by RFC 2616, the status code of the response should be 403 Forbidden.
    *
    * @param request The request header.
    * @return The result to send to the client.
    */
  override def onNotAuthorized(implicit request: RequestHeader): Future[Result] = {
    val timestamp = Platform.currentTime
    log.info(s"Not authorized request: ${request.method} ${request.path} $timestamp")

    val denied = ResponseCode.ACCESS_DENIED
    Future.successful(
      Forbidden(Json.obj(
        "code" -> denied.id,
        "error" -> denied.toString,
        "message" -> "not authenticated",
        "timestamp" -> timestamp
      ))
    )
  }

  /**
    * Called when a user is not authenticated.
    *
    * As defined by RFC 2616, the status code of the response should be 401 Unauthorized.
    *
    * @param request The request header.
    * @return The result to send to the client.
    */
  override def onNotAuthenticated(implicit request: RequestHeader): Future[Result] = {
    val timestamp = Platform.currentTime
    log.info(s"Not authenticated request: ${request.method} ${request.path} $timestamp")

    val denied = ResponseCode.ACCESS_DENIED
    Future.successful(
      Unauthorized(Json.obj(
        "code" -> denied.id,
        "error" -> denied.toString,
        "message" -> "not authenticated",
        "timestamp" -> timestamp
      ))
    )
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    super.onServerError(request, exception)
  }

}
