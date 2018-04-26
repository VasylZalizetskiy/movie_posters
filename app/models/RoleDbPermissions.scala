package models

import java.security.BasicPermission

import com.impactua.bouncer.commons.utils.JsonHelper
import com.impactua.bouncer.commons.models.{User => CommonsUser}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, JsPath, OWrites, Reads}

case class UserPermission(name: String) extends BasicPermission(name.toUpperCase)

case class UserDbPermission(name: String, id: Int = 0) {
  val upperName = name.toUpperCase
}

case class RoleDbPermissions(role: String, permissions: Seq[String]) {
  val roleStr = role.toString
  val permissionsArr = permissions.map(_.toUpperCase).toArray
}

object RoleDbPermissions {

  val COLLECTION_NAME = "user_permissions"

  implicit val reader: Reads[RoleDbPermissions] = (
      (JsPath \ "role").read[String] and
      (JsPath \ "permissions").read[Seq[String]].orElse(Reads.pure(Nil))
    )(RoleDbPermissions.apply _)

  implicit val writer = new OWrites[RoleDbPermissions] {
    def writes(u: RoleDbPermissions): JsObject = JsonHelper.toNonemptyJson(
      "role" -> u.roleStr,
      "permissions" -> u.permissions
    )
  }
}