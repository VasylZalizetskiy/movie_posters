package services

import javax.inject.Inject

import akka.actor.{Actor, ActorSystem, Props}
import com.mohiva.play.silhouette.api.EventBus
import models.Session
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import utils.MongoErrorHandler

import scala.compat.Platform
import scala.concurrent.{ExecutionContext, Future}

trait SessionsService {

  def retrieve(id: String): Future[Option[Session]]

  def store(session: Session): Future[Session]

  def updateExpiration(id: String, expiredAt: Long): Future[Unit]

  def finish(id: String): Future[Unit]

  def remove(id: String): Future[Unit]

}

class MongoSessionsService @Inject()(reactiveMongoApi: ReactiveMongoApi)
                                    (implicit exec: ExecutionContext) extends SessionsService {

  private def db = reactiveMongoApi.database

  private def sessionCollection = db.map(_.collection[JSONCollection](Session.COLLECTION_NAME))

  def retrieve(id: String): Future[Option[Session]] = {
    val selector = Json.obj("_id" -> id)
    sessionCollection.flatMap(_.find(selector).one[Session])
  }

  def store(session: Session): Future[Session] = {
    sessionCollection.flatMap(_.insert(session).map(_ => session).recover(MongoErrorHandler.processError[Session]))
  }

  def updateExpiration(id: String, expiredAt: Long): Future[Unit] = {
    sessionCollection.flatMap(_.update(
      Json.obj("_id" -> id),
      Json.obj("$set" -> Json.obj("expiredAt" -> expiredAt))
    ).map(_ => ()).recover(MongoErrorHandler.processError[Unit]))
  }

  def finish(id: String): Future[Unit] = {
    sessionCollection.flatMap(_.update(
      Json.obj("_id" -> id),
      Json.obj("$set" -> Json.obj("expiredAt" -> Platform.currentTime))
    ).map(_ => ()).recover(MongoErrorHandler.processError[Unit]))
  }

  def remove(id: String): Future[Unit] = {
    sessionCollection.flatMap(_.remove(
      Json.obj("_id" -> id)
    ).map(_ => ()).recover(MongoErrorHandler.processError[Unit]))
  }

}