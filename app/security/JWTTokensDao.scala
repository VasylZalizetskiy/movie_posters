package security

import com.fotolog.redis.{BinaryConverter, RedisClient}
import com.impactua.bouncer.commons.models.ResponseCode
import com.impactua.bouncer.commons.models.exceptions.{AppException, InternalServerErrorException}
import com.impactua.bouncer.commons.utils.Logging
import com.mohiva.play.silhouette.api.crypto.AuthenticatorEncoder
import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.impl.authenticators.{JWTAuthenticator, JWTAuthenticatorSettings}
import net.ceedubs.ficus.Ficus._
import play.api.Configuration
import services.SessionsService
import utils.Responses._

import scala.compat.Platform
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

/**
  * Redis based DAO for JWT tokens.
  *
  */
class JWTTokensDao(settings: JWTAuthenticatorSettings, encoder: AuthenticatorEncoder, conf: Configuration, sessionsService: SessionsService)
  extends AuthenticatorRepository[JWTAuthenticator] with Logging {

  lazy val redis = RedisClient(conf.get[String]("redis.host"))

  val defaultTtl: Int = settings.authenticatorIdleTimeout
    .getOrElse(conf.underlying.as[FiniteDuration]("session.time"))
    .toSeconds.toInt

  val defaultLeftTtl: Int = defaultTtl / 2

  override def find(id: String): Future[Option[JWTAuthenticator]] = {
    redis.getAsync[JWTAuthenticator](id)(converter)
  }

  override def update(authenticator: JWTAuthenticator): Future[JWTAuthenticator] = {
    redis.ttlAsync(authenticator.id) flatMap {
      case -2 | -1 =>
        log.debug(s"Authenticator ${authenticator.id} not found in redis")
        Future.failed(new AppException(ResponseCode.ACCESS_DENIED, "Authenticator not found"))
      case ttl if authenticator.isOauth && !authenticator.isExpired =>
        log.debug(s"Don't update oauth authenticator ${authenticator.id} with ttl $ttl")
        Future.successful(authenticator)
      case expired if authenticator.notOauth && expired <= defaultLeftTtl && !authenticator.isExpired =>
        log.debug(s"Prolong user authenticator ${authenticator.id} with $defaultTtl")
        val expirationTime = Platform.currentTime + defaultTtl * 1000
        redis.setAsync(authenticator.id, authenticator, defaultTtl) map {
          case true =>
            log.debug(s"Update authenticator expired time in redis, left time $expired")
            sessionsService.updateExpiration(authenticator.id, expirationTime)
            authenticator
          case false =>
            throw new InternalServerErrorException("Error occurred on authenticator saving")
        }
      case ttl if !authenticator.isExpired =>
        log.debug(s"Don't update authenticator ${authenticator.id}(isOauth ${authenticator.isOauth}) with ttl $ttl")
        Future.successful(authenticator)
      case _ if authenticator.isExpired => Future.failed(new AppException(ResponseCode.ACCESS_DENIED, "Session expired"))
    }
  }

  override def remove(id: String): Future[Unit] = {
    redis.delAsync(id) map { _ => }
  }

  override def add(authenticator: JWTAuthenticator): Future[JWTAuthenticator] = {
    val authTtl = if (authenticator.isOauth) {
      (authenticator.expirationDateTime.getMillis - Platform.currentTime) / 1000
    } else {
      defaultTtl
    }

    redis.setAsync(authenticator.id, authenticator, authTtl.toInt) map { _ => authenticator }
  }

  implicit val converter = new BinaryConverter[JWTAuthenticator] {
    override def read(data: Array[Byte]): JWTAuthenticator = {
      val authenticator = JWTAuthenticator.unserialize(new String(data), encoder, settings).get
      if (authenticator.isOauth) {
        authenticator.copy(idleTimeout = None)
      } else {
        authenticator
      }
    }

    override def write(v: JWTAuthenticator): Array[Byte] =
      JWTAuthenticator.serialize(v, encoder, settings).getBytes("UTF-8")
  }

}
