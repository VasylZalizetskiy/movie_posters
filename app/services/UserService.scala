package services

import com.impactua.bouncer.commons.models.exceptions.{AppException, InternalServerErrorException, UserNotFoundException}
import com.impactua.bouncer.commons.models.{ResponseCode, User => CommonUser}
import com.impactua.bouncer.commons.utils.Logging
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordInfo
import javax.inject.{Inject, Singleton}
import models.{RoleDbPermissions, User}
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

/**
  * Handles actions to users.
  */
@Singleton
class UserService @Inject()(rolesCache: AsyncCacheApi,
                            reactiveMongoApi: ReactiveMongoApi,
                            conf: Configuration)(implicit exec: ExecutionContext) extends UserIdentityService with Logging {

  private def db = reactiveMongoApi.database

  private def usersCollection = db.map(_.collection[JSONCollection](User.COLLECTION_NAME))

  private def rolesCollection = db.map(_.collection[JSONCollection](RoleDbPermissions.COLLECTION_NAME))

  usersCollection.map(_.indexesManager) map { manager =>
    manager.ensure(Index(Seq("email" -> IndexType.Hashed), background = true, unique = true, sparse = true))
    manager.ensure(Index(Seq("phone" -> IndexType.Hashed), background = true, unique = true, sparse = true))
  }

  /**
    * Retrieves a user that matches the specified login info.
    *
    * @param login The login info to retrieve a user.
    * @return The retrieved user or None if no user could be retrieved for the given login info.
    */
  def retrieve(login: LoginInfo): Future[Option[User]] = getByAnyIdOpt(login.providerKey)

  def retrieve(selector: JsObject): Future[Option[JsObject]] = {
    usersCollection.flatMap(_.find(selector).one[JsObject])
  }

  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  def save(user: User): Future[User] = {
    usersCollection.flatMap(_.insert(user))
      .map(_ => user)
      .recover(processUserDbEx[User](user.uuid))
  }

  def updatePassHash(login: String, pass: PasswordInfo): Future[Unit] = {
    usersCollection.flatMap { users =>
      val criteria = login match {
        case uuid: String if User.checkUuid(uuid) => Json.obj("_id" -> uuid.toLong)
        case email: String if User.checkEmail(email) => Json.obj("email" -> email.toLowerCase)
        case phone: String if User.checkPhone(phone) => Json.obj("phone" -> phone)
      }

      val obj = Json.obj("$set" -> Json.obj("passHash" -> pass.password))

      users.update(criteria, obj).map(Function.const(()))
    }
  }

  def delete(user: User): Future[Unit] = {
    usersCollection.map { users =>

      users.update(
        Json.obj("_id" -> user.uuid),
        Json.obj("$unset" -> Json.obj("email" -> "", "phone" -> "", "passHash" -> ""))
      )
    }
  }

  def getByAnyId(id: String): Future[User] = getByAnyIdOpt(id).map(_ getOrElse (throw new UserNotFoundException(id)))

  def getByAnyIdOpt(id: String): Future[Option[User]] = {
    val user = id match {
      case uuid: String if User.checkUuid(uuid) => usersCollection.flatMap(_.find(Json.obj("_id" -> uuid.toLong)).one[User])
      case email: String if User.checkEmail(email) => usersCollection.flatMap(_.find(Json.obj("email" -> email.toLowerCase)).one[User])
      case phone: String if User.checkPhone(phone) => usersCollection.flatMap(_.find(Json.obj("phone" -> phone)).one[User])
      case _ => Future.successful(None)
    }

    user.flatMap { optUser =>
      optUser.map(withPermissions(_).map(Some(_))).getOrElse(Future.successful(None))
    }
  }

  def withPermissions(u: User): Future[User] =
    loadPermissions(u.roles).map(p => u.copy(permissions = p))


  private def loadPermissions(roles: Seq[String]): Future[Seq[String]] = rolesCache.getOrElseUpdate(roles.mkString(",")) {
    rolesCollection.flatMap(
      _.find(Json.obj("role" -> Json.obj("$in" -> roles)))
        .cursor[RoleDbPermissions](ReadPreference.secondaryPreferred)
        .collect[List](-1, errorHandler[RoleDbPermissions])
        .map(_.flatMap(_.permissions).distinct)
    )
  }

  private def processUserDbEx[T](userId: Long): PartialFunction[Throwable, T] = {
    case ex: DatabaseException if ex.code.exists(c => c == 11000 || c == 11001) =>
      log.error(s"Error occurred on user $userId updating (duplicate identifier) ", ex)
      throw new AppException(ResponseCode.USER_IDENTIFIER_EXISTS, "Email or phone already exists")

    case ex: DatabaseException =>
      log.error(s"Database exception occurred on user $userId updating, code ${ex.code}", ex)
      throw new InternalServerErrorException("Database error occurred on user updating")

    case ex: Exception =>
      log.error(s"Undefined exception occurred on user $userId updating ", ex)
      throw new InternalServerErrorException("Internal error occurred on user updating")
  }

  private def errorHandler[T] = Cursor.ContOnError[List[T]]((v: List[T], ex: Throwable) => {
    log.warn("Error occurred on users reading", ex)
  })

}