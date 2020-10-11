package twita.dominion.api.ex

import play.api.libs.json.JsObject
import play.api.libs.json.Json

class DominionRuntimeException(msg: String) extends RuntimeException(msg)

class ObjectDeleted[ObjectId](id: ObjectId)
  extends DominionRuntimeException(s"id: ${id}")

class ObjectUpdateNotApplied(obj: Object, discriminator: JsObject)
  extends DominionRuntimeException(s"object ${obj.getClass.getName} discriminator ${Json.prettyPrint(discriminator)}")

class ObjectDoesNotMeetConstraints(q: JsObject, constraint: JsObject)
  extends DominionRuntimeException(s"query: ${q.toString}, constraint: ${constraint.toString}")
