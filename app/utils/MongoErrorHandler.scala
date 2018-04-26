package utils

import com.impactua.bouncer.commons.models.ResponseCode
import com.impactua.bouncer.commons.models.exceptions.{AppException, InternalServerErrorException}
import com.impactua.bouncer.commons.utils.Logging
import reactivemongo.core.errors.DatabaseException

object MongoErrorHandler extends Logging {

  private val existsCodeEntity = Seq(11000, 11001, 10054, 10056, 10058, 10107, 13435, 13436)
  private val notfoundCodeEntity = Seq(10057, 15845, 16550)

  def processError[T]: PartialFunction[Throwable, T] = {
    case ex: DatabaseException if ex.code.exists(existsCodeEntity.contains) => throw new AppException(ResponseCode.ALREADY_EXISTS, "Entity already exists")
    case ex: DatabaseException if ex.code.exists(notfoundCodeEntity.contains) => throw new AppException(ResponseCode.ENTITY_NOT_FOUND, "Entity not found")
    case ex: DatabaseException =>
      log.error("Mongo database exception occured", ex)
      throw new InternalServerErrorException("Database exception occurred")
    case ex: Exception =>
      throw new InternalServerErrorException(ex.getMessage)
  }
}