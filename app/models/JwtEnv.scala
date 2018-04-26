package models

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator

trait JwtEnv extends Env {
  type I = User
  type A = JWTAuthenticator
}
