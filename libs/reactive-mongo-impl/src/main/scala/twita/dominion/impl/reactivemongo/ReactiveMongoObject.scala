package twita.dominion.impl.reactivemongo

import java.time.Instant

import play.api.libs.json.Format
import play.api.libs.json.JsDefined
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import reactivemongo.api.WriteConcern
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.compat._
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject
import twita.dominion.api.ex.ObjectDeleted
import twita.dominion.api.ex.ObjectUpdateNotApplied

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait MongoContext {
  def getCollection(name: String): Future[JSONCollection]
}

trait BaseDoc[ObjectId] {
  def _id: ObjectId
}

case class Empty[ObjectId](id: ObjectId)

abstract class ReactiveMongoObject[EventId: Format, A <: DomainObject[EventId, A], D <: BaseDoc[A#ObjectId]: OFormat](
  implicit oidFormat: Format[A#ObjectId], executionContext: ExecutionContext
) extends ObjectDescriptor[EventId, A, D]
{
  protected def underlying: Either[Empty[A#ObjectId], D]
  def id: A#ObjectId = underlying.fold(e => e.id, d => d._id)
  protected lazy val obj: D = underlying.right.getOrElse(throw new ObjectDeleted(id))
  protected lazy val esdOptFt: Future[Option[EventSourcedDoc]] =
    objCollectionFt.flatMap(_.find(Json.obj("_id" -> Json.toJson(id)), projection = Some(Json.obj())).one[EventSourcedDoc])

  def initialState: Future[A] = esdOptFt.map(esdOpt => cons(Right(esdOpt.get._init)))

  def update[E <: AllowedEvent : OWrites](
      obj: D
    , event: E
    , parent: Option[BaseEvent[EventId]]
  ): Future[A] = update(ReactiveMongoObject.SetOp(Json.toJsObject(obj)), event, parent)

  def update[E <: AllowedEvent : OWrites](
      update: ReactiveMongoObject.SetOp
    , event: E
    , parent: Option[BaseEvent[EventId]]
  ): Future[A] = updateVerbose(Json.obj("$set" -> update.json), event, parent)

  def updateVerbose[E <: AllowedEvent : OWrites](
      update: JsObject
    , event: E
    , parent: Option[BaseEvent[EventId]]
    , discriminator: JsObject = Json.obj("_id" -> Json.toJson(id))
  ): Future[A] = {
    // Little helper method that either adds to the $set clause of this update statement if one exists or
    // adds a $set section if one is not present.
    def getUpdateJson(esdOpt: Option[EventSourcedDoc], eventId: EventId) = {
      val updateEventFields = Json.toJsObject(esdOpt.get.copy(_cur = eventId))
      update \ "$set" match {
        case JsDefined(oldSet) => update + ("$set" -> (update \ "$set").as[JsObject].++(updateEventFields))
        case _ => update + ("$set" -> updateEventFields)
      }
    }

    {
      for {
        objColl <- objCollectionFt
        esdOpt <- esdOptFt
        eventDoc = EventMetaData(
            _id = event.generatedId
          , _objId = id
          , _coll = objColl.name
          , _type = event.getClass.getName
          , _created = Instant.now
          , _prev = esdOpt.map(_._cur)
        )
        updateResult <- objColl.update(ordered=false).one(discriminator, getUpdateJson(esdOpt, event.generatedId)).map {
          case r if r.nModified == 0 => throw new ObjectUpdateNotApplied(this, discriminator)
          case r => r
        }
        result <- objColl.find(Json.obj("_id" -> Json.toJson(id)), projection = Some(Json.obj())).one[D].map(newObj => cons(Right(newObj.get)))
        _ <- eventLogger.log(eventDoc, event, parent)
      } yield result
    }
    /*.recoverWith {
      // The magic string below is what checks for whether this event is a duplicate of another event that has
      // already been inserted.  The events collection is assumed to have a unique index on the combination of
      // _objId and _prev, and if 2 events think that they are being applied to the same _prev then we need to
      // fail one of them and reapply it to the re-fetched object.
      case dbEx: DatabaseException if MongoContext.duplicateParentEventExceptionMsg.findFirstMatchIn(dbEx.message).isDefined =>
        for {
          objColl <- context.collectionFt(collectionName)
          obj <- objColl.find(Json.obj("_id" -> id), projection = Some(Json.obj())).one[D].map(newObj => cons(Right(newObj.get)))
          result <- obj.apply(event.asInstanceOf[obj.AllowedEvent])
        } yield result
    }*/
  }

  def delete[E <: AllowedEvent : OWrites](obj: D, event: E, parent: Option[BaseEvent[EventId]]): Future[A] = {
    for {
      objColl <- objCollectionFt
      esdOpt <- esdOptFt
      eventDoc = EventMetaData(
          _id = event.generatedId
        , _objId = id
        , _coll = objColl.name
        , _type = event.getClass.getName
        , _created = Instant.now
        , _prev = esdOpt.map(_._cur)
      )
      _ <- objColl.update(ordered=false).one(Json.obj("_id" -> Json.toJson(id)),
          esdOpt.map(_.copy(_cur = event.generatedId, _deleted = Some(Instant.now))).get
        )
      _ <- eventLogger.log(eventDoc, event, parent)
    } yield cons(Left(Empty(id)))
  }

  protected def purge[E <: AllowedEvent : OWrites](event: E, parent: Option[BaseEvent[EventId]]): Future[A] = {
    for {
      objColl <- objCollectionFt
      esdOpt <- esdOptFt
      eventDoc = EventMetaData(
          _id = event.generatedId
        , _objId = id
        , _coll = objColl.name
        , _type = event.getClass.getName
        , _created = Instant.now
        , _prev = esdOpt.map(_._cur)
      )
      result <- objColl.findAndRemove(
          selector = Json.obj("_id" -> Json.toJson(id))
        , sort = None
        , fields = None
        , writeConcern = WriteConcern.Acknowledged
        , maxTime = None
        , collation = None
        , arrayFilters = Seq.empty
      )
      _ <- eventLogger.log(eventDoc, event, parent)
    } yield cons(Left(Empty(id)))
  }
}

object ReactiveMongoObject {
  /**
    * Wrapper that adds type safety to a mongo \$set operation.  We need to protect (at compile time) against invoking
    * non-\$set operations with the update(json) method, the problem being that such operations will silently fail when
    * run against mongo.
    * @param json json object that contains the field/value pairs to be set in the \$set op.
    */
  case class SetOp(json: JsObject)
}

