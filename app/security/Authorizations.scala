package security

import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import models.User
import play.api.mvc.Request
import utils.Responses._
import scala.concurrent.Future

case class WithPermission(permission: String) extends Authorization[User, JWTAuthenticator] {
  override def isAuthorized[B](identity: User, authenticator: JWTAuthenticator)(implicit request: Request[B]): Future[Boolean] = {
    Future.successful((authenticator.isOauth && authenticator.hasAnyOauthPermission(permission)) || identity.hasPermission(permission))
  }
}