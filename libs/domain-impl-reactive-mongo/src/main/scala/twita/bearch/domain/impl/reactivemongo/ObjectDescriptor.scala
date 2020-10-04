package twita.bearch.domain.impl.reactivemongo

import java.time.Instant

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import reactivemongo.play.json.collection.JSONCollection
import twita.bearch.domain.api.BaseEvent
import twita.bearch.domain.api.DomainObject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * This class serves as the base class for both the Domain Objects and the Domain Object Groups.  These classes
  * both require an understanding of the application domain object and of the format that will be used to store
  * that object in the database.
  *
  * @param context The MongoContext object needs to be passed to all instances of the domain object and domain
  *                object groups.
  * @param oidFormat We need to have available to us the method by which we will be able to serialize and
  *                  deserialize the ObjectId of A, our domain object.
  * @param executionContext
  * @tparam EventId The type or the unique ids for the events that will be applied to this object.
  * @tparam A The application domain object type.
  * @tparam D The representation of A that will be stored in Mongo.
  */
abstract class ObjectDescriptor[
    EventId: Format
  , A <: DomainObject[EventId, A]
  , D <: BaseDoc[A#ObjectId]: OFormat
](protected val context: MongoContext)(implicit oidFormat: Format[A#ObjectId], executionContext: ExecutionContext)
{
  /**
    * @return Eventually returns the JSON collection that instances of this object will be stored in.
    */
  protected def objCollectionFt: Future[JSONCollection]

  /**
    * @return function that can be used to construct an A given an Empty or a D.  This function is used to return the
    *         domain object back to the application after it has been retrieved from the database as a D.
    */
  protected def cons: Either[Empty[A#ObjectId], D] => A

  /**
    * Internal parameters for all domain objects that we store in the database.  Because Mongo is schema-less, we can
    * store additional parameters in the database without needing to change A or D directly.
    * @param _cur The id of the last event that was applied to this object
    * @param _init The intial value of D for this document.  The idea here is that we can store what D looked like
    *              when it was added to the database, then look through the events that were applied to it to see
    *              how this object came to be the way it is now.
    * @param _deleted When an object is soft-deleted, this field is set to the time of the deletion.
    */
  protected case class EventSourcedDoc(
      _cur: EventId
    , _init: D
    , _deleted: Option[Instant] = None
  )
  object EventSourcedDoc { implicit def fmt = Json.format[EventSourcedDoc] }

  case class EventDoc (
      _id: EventId
    , _objId: A#ObjectId
    , _coll: String
    , _type: String
    , _created: java.time.Instant
    , _prev: Option[EventId] = None
  )
  object EventDoc { implicit def fmt = Json.format[EventDoc] }

  trait EventLogger {
    def log[E <: BaseEvent[EventId]: OWrites](eventDoc: EventDoc, event: E, parent: Option[BaseEvent[EventId]]): Future[Unit]
  }

  object NoOpEventLogger extends EventLogger {
    def log[E <: BaseEvent[EventId]: OWrites](eventDoc: EventDoc, event: E, parent: Option[BaseEvent[EventId]]): Future[Unit] = Future.successful(())
  }

  trait MongoEventLogger extends EventLogger {
    protected def evtCollectionFt: Future[JSONCollection]

    def log[E <: BaseEvent[EventId]: OWrites](eventDoc: EventDoc, event: E, parent: Option[BaseEvent[EventId]]): Future[Unit] = for {
      evtColl <- evtCollectionFt
      evtWriteResult <- evtColl.insert(ordered = false).one(
        Json.toJsObject(eventDoc) ++
          Json.toJsObject(event) ++
          JsObject(parent.map(evt => "_parentEventId" -> Json.toJson(evt.generatedId)).toSeq)
      )
    } yield ()
  }

  def eventLogger: EventLogger
}

