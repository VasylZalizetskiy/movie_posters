package models

import play.api.libs.json.{Json, OFormat}

case class Movie(title: String, description: Option[String], year: Option[Int], imdb: Option[String], posterURL: Option[String])

object Movie {

  val COLLECTION_NAME = "movies"

  implicit val format: OFormat[Movie] = Json.format[Movie]

}