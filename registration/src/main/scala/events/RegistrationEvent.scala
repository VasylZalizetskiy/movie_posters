package events

import java.util.UUID

import com.impactua.kafka.Event
import com.mohiva.play.silhouette.api.SilhouetteEvent
import models.User
import play.api.mvc.RequestHeader

trait AppEvent extends SilhouetteEvent with Event {
  def id: String = UUID.randomUUID().toString
  def external: Boolean = false
}

/**
  * Indicates that event obtained from external server (by Kafka).
  */
trait ExternalEvent extends AppEvent {
  override def external = true
}

class BaseAppEvent(val name: String, val key: String) extends AppEvent

case class Signup(user: User, request: RequestHeader) extends BaseAppEvent("signup", user.uuidStr)
case class ReferralRegistrationEvent(user: User, inviter: Long) extends BaseAppEvent("referral_registration", user.uuidStr)
