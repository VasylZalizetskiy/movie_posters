package controllers

//scalastyle:off public.methods.have.type

import akka.util.ByteString
import com.impactua.bouncer.commons.models.ResponseCode
import com.impactua.bouncer.commons.models.exceptions.AppException
import javax.inject.{Inject, Singleton}
import com.impactua.bouncer.commons.utils.RichJson._
import com.impactua.bouncer.commons.utils.RichRequest._
import com.mohiva.play.silhouette.api.Silhouette
import forms.MovieForm
import models.{JwtEnv, Movie}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.InjectedController
import security.WithPermission
import services.MovieService

import scala.concurrent.ExecutionContext

@Singleton
class MovieController @Inject()(silh: Silhouette[JwtEnv], movies: MovieService)(implicit exec: ExecutionContext) extends InjectedController {
  //val editMoviePermission = WithPermission("edit:movies")
  val editMoviePermission = WithPermission("users:read")


  def save = silh.SecuredAction(editMoviePermission).async(parse.json) { implicit request =>
    val user = request.identity
    val movie = request.asForm(MovieForm.movie)

    movies.save(movie).map { savedMovie =>
      Logger.info(s"Movie ${savedMovie.title} was added by $user")
      Ok(Json.toJson(savedMovie))
    }
  }

  def update = silh.SecuredAction(editMoviePermission).async(parse.json) { implicit request =>
    val user = request.identity
    val movie = request.asForm(MovieForm.movie)

    movies.update(movie).map { updatedMovie =>
      Logger.info(s"Movie ${updatedMovie.title} was updated by $user")
      Ok(Json.toJson(updatedMovie))
    }
  }

  def get(title: String) = Action.async { implicit request =>
    movies.get(title).map { movie =>
      Logger.info(s"Reading movie ${movie.title}")
      Ok(Json.toJson(movie))
    }
  }

  def list = Action.async { request =>
    movies.list.map { list =>
      Logger.info(s"Listing movies")
      Ok(Json.obj(
        "items" -> list.map(toJson),
        "counts" -> list.length
      ))
    }
  }

  def remove(title: String) = silh.SecuredAction(editMoviePermission).async { implicit request =>
    movies.remove(title).map { movie =>
      Logger.info(s"Restriction ${movie.title} was removed by ${request.identity}")
      NoContent
    }
  }

  private def toJson(movie: Movie) = Json.toJson(movie).as[JsObject]

}