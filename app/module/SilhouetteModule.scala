package module

import javax.inject.Singleton
import _root_.services._
import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette.api.actions.{SecuredErrorHandler, UnsecuredErrorHandler}
import com.mohiva.play.silhouette.api.crypto.{Base64AuthenticatorEncoder, Crypter, CrypterAuthenticatorEncoder, Signer}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services.{AuthenticatorService, AvatarService, IdentityService}
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{Environment, EventBus, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.crypto._
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.{CookieSecretProvider, CookieSecretSettings}
import com.mohiva.play.silhouette.impl.providers.state.{CsrfStateItemHandler, CsrfStateSettings}
import com.mohiva.play.silhouette.impl.services._
import com.mohiva.play.silhouette.impl.util._
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.daos.{DelegableAuthInfoDAO, InMemoryAuthInfoDAO}
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import models.dao._
import models.{JwtEnv, User}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration
import play.api.libs.ws.WSClient
import security.{CustomJWTAuthenticatorService, JWTTokensDao}
import utils.ServerErrorHandler

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * The Guice module which wires all Silhouette dependencies.
  */
class SilhouetteModule extends AbstractModule with ScalaModule with EnumerationReader {

  private val HASHER_ROUNDS = 5

  /**
    * Configures the module.
    */
  def configure() {
    bind[IdentityService[User]].to[UserService]
    bind[Silhouette[JwtEnv]].to[SilhouetteProvider[JwtEnv]]
    bind[CacheLayer].to[PlayCacheLayer]
    bind[IDGenerator].toInstance(new SecureRandomIDGenerator())
    bind[PasswordHasher].toInstance(new BCryptPasswordHasher(HASHER_ROUNDS))
    bind[Clock].toInstance(Clock())
    bind[SessionsService].to[MongoSessionsService]

    // Replace this with the bindings to your concrete DAOs
    bind[DelegableAuthInfoDAO[PasswordInfo]].to[PasswordInfoDao]
    bind[DelegableAuthInfoDAO[OAuth1Info]].toInstance(new InMemoryAuthInfoDAO[OAuth1Info])
    bind[DelegableAuthInfoDAO[OAuth2Info]].toInstance(new InMemoryAuthInfoDAO[OAuth2Info])
    bind[DelegableAuthInfoDAO[OpenIDInfo]].toInstance(new InMemoryAuthInfoDAO[OpenIDInfo])
  }

  /**
    * Provides the HTTP layer implementation.
    *
    * @param client Play's WS client.
    * @return The HTTP layer implementation.
    */
  @Provides
  def provideHTTPLayer(client: WSClient): HTTPLayer = new PlayHTTPLayer(client)

  /**
    * Provides the Silhouette environment.
    *
    * @param userService The user service implementation.
    * @param authService The authentication service implementation.
    * @param eventBus The event bus instance.
    * @return The Silhouette environment.
    */
  @Provides
  def provideEnvironment(userService: UserService,
                         authService: AuthenticatorService[JWTAuthenticator],
                         eventBus: EventBus): Environment[JwtEnv] = {

    Environment[JwtEnv](userService, authService, Seq(), eventBus)
  }


  /**
    * Provides the authenticator service.
    *
    * @param idGenerator The ID generator implementation.
    * @param conf The Play configuration.
    * @param clock The clock instance.
    * @return The authenticator service.
    */
  @Provides
  @Singleton
  def provideAuthenticatorService(idGenerator: IDGenerator,
                                  conf: Configuration,
                                  @Named("authenticator-crypter") crypter: Crypter,
                                  sessionsService: SessionsService,
                                  clock: Clock): AuthenticatorService[JWTAuthenticator] = {

    val settings = conf.underlying.as[JWTAuthenticatorSettings]("silhouette.authenticator")
    val encoder = conf.get[String]("silhouette.authenticator.encoder") match {
      case "base64" => new Base64AuthenticatorEncoder()
      case "crypter" => new CrypterAuthenticatorEncoder(crypter)
      case other =>
        throw new IllegalArgumentException(
          s"Setting silhouette.authenticator.encoder(AUTHENTICATOR_ENCODER) value $other is not supported." +
          s"Use: base64 or crypter"
        )
    }

    val store = conf.getOptional[String]("silhouette.authenticator.store").map(_.toLowerCase) match {
      case Some("none") => None
      case Some("redis") => Some(new JWTTokensDao(settings, encoder, conf, sessionsService))
      case Some(other) => throw new IllegalArgumentException(
        s"Setting silhouette.authenticator.store(AUTHENTICATOR_STORE) value $other is not supported." +
          s"Use: none, redis")
    }

    new CustomJWTAuthenticatorService(settings, store, encoder, idGenerator, clock)
  }


  @Provides
  @Named("authenticator-signer")
  def provideAuthenticatorCookieSigner(configuration: Configuration): Signer = {
    val config = configuration.underlying.as[JcaSignerSettings]("silhouette.authenticator.signer")
    new JcaSigner(config)
  }

  /**
    * Provides the crypter for the OAuth1 token secret provider.
    *
    * @param configuration The Play configuration.
    * @return The crypter for the OAuth1 token secret provider.
    */
  @Provides
  @Named("authenticator-crypter")
  def provideOAuth1TokenSecretCrypter(configuration: Configuration): Crypter = {
    val config = configuration.underlying.as[JcaCrypterSettings]("silhouette.authenticator.crypter")
    new JcaCrypter(config)
  }

  /**
    * Provides the auth info repository.
    *
    * @param passwordInfoDAO The implementation of the delegable password auth info DAO.
    * @return The auth info repository instance.
    */
  @Provides
  def provideAuthInfoRepository(passwordInfoDAO: DelegableAuthInfoDAO[PasswordInfo],
                                oAuth1InfoDao: DelegableAuthInfoDAO[OAuth1Info],
                                oAuth2InfoDao: DelegableAuthInfoDAO[OAuth2Info]): AuthInfoRepository = {
    new DelegableAuthInfoRepository(passwordInfoDAO, oAuth1InfoDao, oAuth2InfoDao)
  }

  /**
    * Provides the avatar service.
    *
    * @param httpLayer The HTTP layer implementation.
    * @return The avatar service implementation.
    */
  @Provides
  def provideAvatarService(httpLayer: HTTPLayer): AvatarService = new GravatarService(httpLayer)

  /**
    * Provides the password hasher registry.
    *
    * @param passwordHasher The default password hasher implementation.
    * @return The password hasher registry.
    */
  @Provides
  def providePasswordHasherRegistry(passwordHasher: PasswordHasher): PasswordHasherRegistry = {
    PasswordHasherRegistry(passwordHasher)
  }

  /**
    * Provides the credentials provider.
    *
    * @param authInfoRepository The auth info repository implementation.
    * @param passwordHasherRegistry The password hasher registry.
    * @return The credentials provider.
    */
  @Provides
  def provideCredentialsProvider(
                                  authInfoRepository: AuthInfoRepository,
                                  passwordHasherRegistry: PasswordHasherRegistry): CredentialsProvider = {
    new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
  }




  @Provides
  def provideOAuth1TokenSecretProvider(configuration: Configuration,
                                       @Named("authenticator-signer") cookieSigner: Signer,
                                       @Named("authenticator-crypter") crypter: Crypter,
                                       clock: Clock): OAuth1TokenSecretProvider = {
    val settings = configuration.underlying.as[CookieSecretSettings]("silhouette.oauth1TokenSecretProvider")
    new CookieSecretProvider(settings, cookieSigner, crypter, clock)
  }

  @Provides
  @Named("auth2-cookie-signer")
  def provideOAuth2StageCookieSigner(configuration: Configuration): Signer = {
    val config = configuration.underlying.as[JcaSignerSettings]("silhouette.oauth2StateProvider.cookie.signer")
    new JcaSigner(config)
  }

  @Provides
  def socialStateHandler(@Named("authenticator-signer") signer: Signer,
                         idGenerator: IDGenerator,
                         configuration: Configuration): SocialStateHandler = {
    val csrfSettings = configuration.underlying.as[CsrfStateSettings]("silhouette.oauth2StateProvider")

    val csrfHandler = new CsrfStateItemHandler(csrfSettings, idGenerator, signer)

    new DefaultSocialStateHandler(Set(csrfHandler), signer)
  }

}
