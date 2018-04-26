package models

import com.impactua.bouncer.commons.utils.RichRequest._
import play.api.libs.json._
import play.api.mvc.RequestHeader

import scala.compat.Platform

case class Session(_id: String,
                   userId: Long,
                   createdAt: Long,
                   expiredAt: Long,
                   token: String,
                   agent: String,
                   ip: String) {
  val onlineTime = expiredAt - createdAt
}

object Session {

  val COLLECTION_NAME = "sessions"

  def apply(_id: String, userId: Long, expiredAt: Long, token: String, req: RequestHeader): Session = {
    new Session(_id, userId, Platform.currentTime, expiredAt, token, req.clientAgent, req.clientIp)
  }

  implicit val sessionFmt: OFormat[Session] = Json.format[Session]
}

