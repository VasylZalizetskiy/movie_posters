package services

import com.mohiva.play.silhouette.api.services.IdentityService
import models.User
import play.api.libs.json.JsObject

import scala.concurrent.Future

trait UserIdentityService extends IdentityService[User] {

  def save(user: User): Future[User]

  def retrieve(selector: JsObject): Future[Option[JsObject]]

}