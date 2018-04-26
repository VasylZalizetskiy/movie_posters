package controllers.swagger

import javax.inject.Inject
import com.iheart.playSwagger.SwaggerSpecGenerator
import play.api.Configuration
import play.api.mvc.InjectedController
import controllers.Assets

import scala.concurrent.{ExecutionContext, Future}

class ApiSpecs @Inject()(config: Configuration)(implicit ctx: ExecutionContext) extends InjectedController {

  val swaggerConf = config.get[Configuration]("swagger")

  implicit val cl = getClass.getClassLoader

  private lazy val generator = SwaggerSpecGenerator(swaggerConf.get[Boolean]("swaggerV3"), "forms", "models")

  def specs = Action.async { _ =>
    Future.fromTry(generator.generate()).map(Ok(_))
  }

  def docsIndex = Action {
    val apiUrl = config.get[String]("swagger.path").stripSuffix("/") + "/docs/api.json"
    Ok(views.html.swagger(apiUrl, swaggerConf))
  }

  def docsResources(file: String) = Assets.at("/public/lib/swagger-ui", file)

}