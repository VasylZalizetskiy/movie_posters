package utils

import play.api.libs.json._

import scala.language.dynamics

object RichJson {
  implicit class RichJsonObject(val js: JsObject) extends AnyVal {
    def only(fields: String*): JsObject = JsObject(js.fields.filter(f => fields.contains(f._1)))
    def without(fields: String*): JsObject = JsObject(js.fields.filterNot(f => fields.contains(f._1)))
    def withoutFields(fields: String*): JsObject = filterFields(n => !fields.contains(n))
    def filterNull: JsObject = JsObject(js.fields.filter(_._2 != JsNull))

    def rename(from: String, to: String): JsObject = JsObject(
      (Seq(to -> (js \ from).get) ++ js.fields.filterNot(kv => from.equals(kv._1))).toMap
    )

    def filterFields(f: String => Boolean): JsObject = JsObject(js.fields.filter(p => f(p._1)))

    def withFields(fields: (String, JsValue)*): JsObject = JsObject(js.fields ++ fields)

    def hasField(field: String): Boolean = js.fields.exists(p => field.equals(p._1))

  }

}