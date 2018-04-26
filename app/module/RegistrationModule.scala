package module

import net.codingwell.scalaguice.ScalaModule
import play.api.{Configuration, Environment}
import services._
import services.impl.OpenRegistrationService

class RegistrationModule(environment: Environment,
                         configuration: Configuration) extends ScalaModule {

  override def configure(): Unit = {
    bind[UserIdentityService].to[UserService]

    bind[RegistrationService].to[OpenRegistrationService].asEagerSingleton()
  }
}