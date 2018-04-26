package services

import com.impactua.bouncer.commons.models.ResponseCode
import com.impactua.bouncer.commons.models.exceptions.AppException
import javax.inject.{Inject, Singleton}
import models.Movie
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import utils.MongoErrorHandler

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MovieService @Inject()(mongoApi: ReactiveMongoApi)(implicit ctx: ExecutionContext) {

  private def collection = mongoApi.database.map(_.collection[JSONCollection](Movie.COLLECTION_NAME))

  def save(movie: Movie): Future[Movie] = {
    collection.flatMap(_.insert(movie).map(_ => movie).recover(MongoErrorHandler.processError))
  }

  def update(movie: Movie): Future[Movie] = {
    collection.flatMap(_.findAndUpdate(Json.obj("title" -> movie.title), movie, true).map(
      _.result[Movie].getOrElse(throw new AppException(ResponseCode.ENTITY_NOT_FOUND, s"Movie ${movie.title} isn't found"))
    ))
  }

  def get(title: String): Future[Movie] = {
    collection.flatMap(_.find(Json.obj("title" -> title)).one[Movie]).map(
      _.getOrElse(throw new AppException(ResponseCode.ENTITY_NOT_FOUND, s"Movie $title isn't found"))
    )
  }

  def list: Future[List[Movie]] = {
    collection.flatMap(_.find(JsObject(Nil))
      .cursor[Movie](ReadPreference.secondaryPreferred).collect[List](-1, Cursor.FailOnError[List[Movie]]()))
  }

  def remove(title: String): Future[Movie] = {
    collection.flatMap(_.findAndRemove(Json.obj("_id" -> title)).map(
      _.result[Movie].getOrElse(throw new AppException(ResponseCode.ENTITY_NOT_FOUND, s"Movie $title isn't found"))
    ))
  }

}