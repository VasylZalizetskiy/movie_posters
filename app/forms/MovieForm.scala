package forms

import models.Movie
import play.api.data.Form
import play.api.data.Forms.{optional, _}

object MovieForm {

  val movie = Form(
    mapping(
      "title" -> text(3, 1024),
      "description" -> optional(text(3, 4096)),
      "year" -> optional(number),
      "imdb" -> optional(text),
      "posterURL" -> optional(text)
    )(Movie.apply)(Movie.unapply)
  )

}