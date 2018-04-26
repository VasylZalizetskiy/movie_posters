package controllers

//scalastyle:off public.methods.have.type

import akka.http.scaladsl.util.FastFuture
import com.impactua.bouncer.commons.models.ResponseCode.{AUTHORIZATION_FAILED, BLOCKED_USER, INVALID_PASSWORD, USER_NOT_FOUND}
import com.impactua.bouncer.commons.models.exceptions.AppException
import com.impactua.bouncer.commons.models.{ResponseCode, User => CommonsUser}
import com.impactua.bouncer.commons.security.ConfirmationProvider
import com.impactua.bouncer.commons.utils.JsonHelper
import com.impactua.bouncer.commons.utils.RichRequest._
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.util.{Credentials, PasswordHasher, PasswordInfo}
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import forms.LoginForm.LoginCredentials
import javax.inject.{Inject, Singleton}
import forms.LoginForm
import models.User._
import models._
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.json._
import play.api.mvc.{InjectedController, RequestHeader, Result}
import play.api.{Configuration, Logger}
import services.{RegistrationService, UserService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationController @Inject()(
                                silh: Silhouette[JwtEnv],
                                userService: UserService,
                                registration: RegistrationService,
                                passDao: DelegableAuthInfoDAO[PasswordInfo],
                                passwordHasher: PasswordHasher,
                                confirmationValidator: ConfirmationProvider,
                                credentialsProvider: CredentialsProvider,
                                config: Configuration
                              )(implicit exec: ExecutionContext) extends InjectedController with I18nSupport {

  implicit def listUserWrites = new Writes[Seq[User]] {
    override def writes(o: Seq[User]): JsValue = JsArray(for (obj <- o) yield Json.toJson(obj))
  }

  val requirePass = config.getOptional[Boolean]("app.requirePassword").getOrElse(true)

  /**
    * Authenticates a user against the credentials provider.
    *
    * @return The result to display.
    */
  def login = Action.async(parse.json) { implicit request =>
    val credentials = request.asForm(LoginForm.form)

    val futureResult = for {
      loginInfo <- credsTologinInfo(credentials)
      optUser   <- userService.retrieve(loginInfo)
      result    <- processUserLogin(optUser, loginInfo, credentials)
    } yield result

    futureResult.recover {
      case e: IdentityNotFoundException =>
        Logger.info(s"User: ${credentials.login} not found " + e.getMessage)
        throw new AppException(AUTHORIZATION_FAILED, Messages("invalid.credentials"))
      case e: InvalidPasswordException =>
        Logger.info(s"Password for user: ${credentials.login} not match " + e.getMessage)
        throw new AppException(AUTHORIZATION_FAILED, Messages("invalid.credentials"))
      case e: ProviderException =>
        Logger.info(s"Invalid credentials for user: ${credentials.login} " + e.getMessage)
        throw new AppException(AUTHORIZATION_FAILED, Messages("invalid.credentials"))
      case e: AppException =>
        Logger.info(s"App exception occurred for user: ${credentials.login} " + e.message)
        throw e
    }
  }

  def register = silh.UserAwareAction.async(parse.json) { implicit request =>
    //One step registration...
    registration.userRegistrationRequest(request).flatMap { regData =>
      registration.confirmUserRegistrationRequest(regData.login).map { u =>
        Ok(Json.toJson(u))
      }
    }
  }

  private def credsTologinInfo(creds: LoginCredentials) = creds.password match {
    case Some(pass) =>
      credentialsProvider.authenticate(Credentials(creds.loginFormatted, pass))
    case None if requirePass =>
      Logger.warn(s"Password for user ${creds.login} required but not set")
      throw new AppException(INVALID_PASSWORD, "Invalid password")

    case None if !requirePass =>
      val credentials = Credentials(creds.loginFormatted, "eternal-pass")
      FastFuture.successful(LoginInfo(CredentialsProvider.ID, credentials.identifier))
  }

  private def processUserLogin(optUser: Option[User], loginInfo: LoginInfo,
                               credentials: LoginCredentials)(implicit request: RequestHeader): Future[Result] = optUser match {
    case Some(user) if user.hasFlag(CommonsUser.Flag.BLOCKED.toString) =>
      Logger.warn(s"User ${credentials.login} is blocked")
      throw new AppException(BLOCKED_USER, s"User ${credentials.login} is blocked")

    case Some(user) if user.hasFlag(CommonsUser.Flag.LOGIN_CONFIRMED) && credentials.password.isDefined =>
      silh.env.authenticatorService.create(loginInfo) flatMap { authenticator =>
        silh.env.authenticatorService.init(authenticator).flatMap { token =>
          Logger.info(s"Succeed user authentication ${loginInfo.providerKey}")

          silh.env.authenticatorService.embed(token, Ok(JsonHelper.toNonemptyJson("token" -> token)))
        }
      }

    case Some(_) =>
      Logger.info(s"User ${credentials.login} haven't confirmed login")
      throw new AppException(ResponseCode.USER_NOT_CONFIRMED, "User has to be confirmed first")

    case None =>
      Logger.warn(s"User with login: ${credentials.login} is not found")
      throw new AppException(USER_NOT_FOUND, s"User with login: ${credentials.login} is not found")
  }

}