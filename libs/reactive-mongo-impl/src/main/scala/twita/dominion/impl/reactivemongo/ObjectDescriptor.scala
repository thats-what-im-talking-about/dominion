package twita.dominion.impl.reactivemongo

import java.time.Instant

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.compat._
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * This class serves as the base class for both the Domain Objects and the Domain Object Groups.  These classes
  * both require an understanding of the application domain object and of the format that will be used to store
  * that object in the database.
  *
  * @param oidFormat We need to have available to us the method by which we will be able to serialize and
  *                  deserialize the ObjectId of A, our domain object.
  * @param executionContext
  * @tparam EventId The type of the unique ids for the events that will be applied to this object.
  * @tparam A The application domain object type.
  * @tparam D The case class representation of A that will be stored in Mongo.
  */
abstract class ObjectDescriptor[
    EventId: Format
  , A <: DomainObject[EventId, A]
  , D <: BaseDoc[A#ObjectId]: OFormat
](implicit oidFormat: Format[A#ObjectId], val executionContext: ExecutionContext)
{
  type AllowedEvent <: BaseEvent[EventId]

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

  /*
   *                   E   V   E   N   T       L   O   G   G   I   N   G
   *
   * Everything from here on is in support of event logging functionality.  Implementers may choose to also define
   * their own EventLogger instances in an application specific extension of ObjectDescriptor.  By default, the
   * NoOpEventLogger will be put in place.
   */

  /**
    * Default internal fields that are part of every event that is logged in the system.
    * @param _id unique ID for this event.
    * @param _objId the unique id of the D instance that is being manipulated.
    * @param _coll the name of the collection that is being specified by this ObjectDescriptor.
    * @param _type the fully qualified class name of the event being processed.
    * @param _created timestamp of when the event was created.
    * @param _prev the _id value for the event that came before this one (can be used to string the events together
    *              to get an idea of the sequence of operations performed against a particular domain object).
    */
  case class EventMetaData(
      _id: EventId
    , _objId: A#ObjectId
    , _coll: String
    , _type: String
    , _created: java.time.Instant
    , _prev: Option[EventId] = None
  )
  object EventMetaData { implicit def fmt = Json.format[EventMetaData] }

  /**
    * Simple interface that defines the interface that will be used to log events against this domain object.
    */
  trait EventLogger {
    /**
      * Contract for logging events applied to this {{ObjectDescriptor}}
      * @param eventMetaData EventMetaData instance that describes the metadata about this event.
      * @param event The actual event instance that was applied to this {{ObjectDescriptor}}.
      * @param parent
      * @tparam E
      * @return
      */
    def log[E <: AllowedEvent: OWrites](eventMetaData: EventMetaData, event: E, parent: Option[BaseEvent[EventId]]): Future[Unit]
  }

  /**
    * @return The EventLogger instance to be used with this object.  By default, we will return a {{NoOpEventLogger}},
    *         but there are a couple of others defined in this class and implementers may choose to implement their
    *         own as well.
    */
  def eventLogger: EventLogger = NoOpEventLogger

  object NoOpEventLogger extends EventLogger {
    def log[E <: AllowedEvent: OWrites](eventDoc: EventMetaData, event: E, parent: Option[BaseEvent[EventId]]): Future[Unit] = Future.successful(())
  }

  /**
    * Logs all of the events that are applied to this object into an implementation-provided JSONCollection.  It is
    * up to the implementer whether these events all get saved to one collection or if there are per-module or even
    * per domain object event collections.
    */
  trait MongoEventLogger extends EventLogger {
    protected def evtCollectionFt: Future[JSONCollection]

    def log[E <: AllowedEvent: OWrites](eventDoc: EventMetaData, event: E, parent: Option[BaseEvent[EventId]]): Future[Unit] = for {
      evtColl <- evtCollectionFt
      evtWriteResult <- evtColl.insert(ordered = false).one(
        Json.toJsObject(eventDoc) ++
          Json.toJsObject(event) ++
          JsObject(parent.map(evt => "_parentEventId" -> Json.toJson(evt.generatedId)).toSeq)
      )
    } yield ()
  }

  class MongoObjectEventStackLogger(depth: Int) extends EventLogger {
    case class EventStack(_eventStack: Option[List[JsObject]] = None)
    object EventStack { implicit val fmt = Json.format[EventStack] }

    override def log[E <: AllowedEvent: OWrites](eventDoc: EventMetaData, event: E, parent: Option[BaseEvent[EventId]]): Future[Unit] = {
      for {
        objColl <- objCollectionFt
        eventJson = Json.toJsObject(eventDoc) ++ Json.toJsObject(event)
        writeResult <- objColl.update(ordered=false).one(
            Json.obj("_id" -> eventDoc._objId)
          , Json.obj("$push" -> Json.obj("_eventStack" -> Json.obj("$each" -> Seq(eventJson), "$slice" -> -1*depth)))
        )
      } yield ()
    }
  }
}
