package models

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordInfo

case class RegisterInfo(loginInfo: LoginInfo, user: User, passInfo: PasswordInfo)