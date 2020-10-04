package twita.bearch.domain

import play.api.libs.json.JsObject
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat

package object api {

  /**
    * Convenience trait that connects the EventSourced trait together with the HasId trait.  The idea was just that
    * it looks nicer to have
    * <pre>
    *   trait User extends DomainObject[EventId, User]
    * </pre>
    * than it does to have
    * <pre>
    *   trait User extends EventSources[User, EventId] with HasId
    * </pre>
    *
    * @tparam EventId the arbitrary type that will be used to uniquely identify events that have been applied.
    * @tparam A The domain object type
    */
  trait DomainObject[EventId, A <: HasId] extends EventSourced[A, EventId] with HasId

  case class EmptyEventFmt[E](instance: E) extends OFormat[E] {
    override def reads(json: JsValue): JsResult[E] = JsSuccess(instance)
    override def writes(o: E): JsObject = Json.obj()
  }
}
