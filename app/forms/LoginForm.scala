package forms

import com.impactua.bouncer.commons.utils.FormConstraints._
import play.api.data.Form
import play.api.data.Forms._

/**
 * The form which handles the submission of the credentials.
 */
object LoginForm {

  val form = Form(
    mapping(
      "login" -> nonEmptyText.verifying(or(emailAddress, phoneNumber)),
      "password" -> optional(password)
    )(LoginCredentials.apply)(LoginCredentials.unapply)
  )

  case class LoginCredentials(login: String, password: Option[String]) {
    def loginFormatted: String = {
      if (login.startsWith("\\+")) {
        login.substring(1)
      } else {
        login
      }
    }
  }
}

