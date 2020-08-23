package twita.bearch.domain.api.ex

import play.api.libs.json.JsObject
import play.api.libs.json.Json

class BearchRuntimeException(msg: String) extends RuntimeException(msg)

class ObjectDeleted[ObjectId](id: ObjectId, collection: String) 
  extends BearchRuntimeException(s"id: ${id}, coll: ${collection}")

class ObjectUpdateNotApplied(obj: Object, discriminator: JsObject)
  extends BearchRuntimeException(s"object ${obj.getClass.getName} discriminator ${Json.prettyPrint(discriminator)}")
