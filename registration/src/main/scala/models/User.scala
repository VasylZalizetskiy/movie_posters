package models

import java.security.{Permission, Permissions}
import java.util.{Calendar, Date}

import com.impactua.bouncer.commons.models.User.UserPermission
import com.impactua.bouncer.commons.models.{User => CommonsUser}
import com.impactua.bouncer.commons.utils.JsonHelper
import com.impactua.bouncer.commons.utils.StringHelpers.{isNumberString, isValidEmail, isValidPhone}
import com.mohiva.play.silhouette.api.Identity
import com.mohiva.play.silhouette.impl.providers.{OAuth1Info, OAuth2Info}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.UuidGenerator

import scala.compat.Platform

case class User(uuid: Long = UuidGenerator.generateId,
                email: Option[String],
                phone: Option[String],
                passHash: String,
                flags: Seq[String] = Nil,
                roles: Seq[String] = Nil,
                permissions: Seq[String] = Nil,
                firstName: Option[String] = None,
                lastName: Option[String] = None,
                created: Date = new Date()
               ) extends Identity {

  def fullName: Option[String] = {
    for {
      first <- firstName
      last <- lastName
    } yield first + " " + last
  }

  lazy val javaPermissions: java.security.Permissions = {
    val perm = new Permissions()
    permissions.foreach(p => perm.add(UserPermission(p.toUpperCase)))
    perm
  }

  val uuidStr = uuid.toString

  def identifier: String = email getOrElse (phone getOrElse uuid).toString

  def checkId(anyId: String): Boolean = email.contains(anyId) || phone.contains(anyId) || uuid.toString == anyId

  def hasRole(r: String): Boolean = roles.contains(r)

  def hasAnyRole(r: String*): Boolean = roles.exists(hasRole)

  def hasPermission(p: String): Boolean = permissions.contains(p)

  def hasPermission(p: Permission): Boolean = javaPermissions.implies(p)

  def hasAllPermission(p: String*): Boolean = p.forall(hasPermission)

  def hasAnyPermission(p: String*): Boolean = p.exists(hasPermission)

  def withFlags(addFlags: String*): User = copy(flags = (flags ++ addFlags).distinct)

  def withoutFlags(removeFlags: String*): User = copy(flags = flags diff removeFlags)

  def hasFlag(flag: String): Boolean = flags.contains(flag)

  def hasAllFlags(flags: String*): Boolean = !flags.exists(!hasFlag(_))

  def adminFlagsChanged(flags: Seq[String]): Boolean = {

    val newAdminsSet = flags.filter(f => CommonsUser.Flag.ADMIN_FLAGS.contains(f)).sorted
    val currentAdminsSet = this.flags.filter(f => CommonsUser.Flag.ADMIN_FLAGS.contains(f)).sorted

    newAdminsSet.equals(currentAdminsSet)
  }

}

object User {

  implicit val oAuth1InfoFmt = Json.format[OAuth1Info]
  implicit val oAuth2InfoFmt = Json.format[OAuth2Info]

  val COLLECTION_NAME = "users"

  def checkAnyId(anyId: String): Boolean = checkUuid(anyId) || checkEmail(anyId) || checkPhone(anyId)

  def checkUuid(id: String): Boolean = isNumberString(id) && id.length == 16

  def checkPhone(id: String): Boolean = isValidPhone(id)

  def checkEmail(id: String): Boolean = isValidEmail(id)

  def checkSocialProviderKey(id: String): Boolean = isNumberString(id)

  implicit val reader: Reads[User] = (
    (JsPath \ "_id").read[Long] and
      (JsPath \ "email").readNullable[String] and
      (JsPath \ "phone").readNullable[String] and
      (JsPath \ "passHash").read[String].orElse(Reads.pure("")) and
      (JsPath \ "flags").read[Seq[String]].orElse(Reads.pure(Nil)) and
      (JsPath \ "roles").read[Seq[String]].orElse(Reads.pure(Nil)) and
      (JsPath \ "permissions").read[Seq[String]].orElse(Reads.pure(Nil)) and
      (JsPath \ "firstName").readNullable[String] and
      (JsPath \ "lastName").readNullable[String] and
      (JsPath \ "created").read[Date]
    ) (User.apply _)

  implicit val oWriter = new OWrites[User] {
    def writes(u: User): JsObject = {
      JsonHelper.toNonemptyJson(
        "_id" -> u.uuid,
        "email" -> u.email,
        "phone" -> u.phone,
        "passHash" -> u.passHash,
        "flags" -> u.flags,
        "roles" -> u.roles,
        "firstName" -> u.firstName,
        "lastName" -> u.lastName,
        "created" -> u.created
      )
    }
  }

}