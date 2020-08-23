package twita.bearch.domain.impl.reactivemongo

import java.time.Instant

import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.compat._
import com.typesafe.config.Config
import play.api.libs.json.Format
import play.api.libs.json.JsDefined
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import reactivemongo.api.WriteConcern
import reactivemongo.api.commands.WriteResult
import twita.bearch.domain.api.BaseEvent
import twita.bearch.domain.api.EventSourced
import twita.bearch.domain.api.ex.ObjectDeleted
import twita.bearch.domain.api.ex.ObjectUpdateNotApplied

import scala.concurrent.ExecutionContext
import scala.concurrent.Future


trait MongoContext {
  def collectionFt(name: String): Future[JSONCollection]
  def config: Config
}

trait BaseDoc[ObjectId] {
  def _id: ObjectId
}

trait ObjectDescriptor[ObjectId, EventId, A <: EventSourced[A, EventId], D <: BaseDoc[ObjectId]] {
  protected def moduleName: String
  protected def context: MongoContext
  protected def collectionName: String
  protected def collectionFt: Future[JSONCollection] = context.collectionFt(collectionName)
  protected lazy val evtCollectionName = s"${moduleName}.events"
  implicit val config = context.config

  case class Empty[ObjectId](id: ObjectId)

  protected def cons: Either[Empty[ObjectId], D] => A

  case class EventDoc[EventId, ObjectId] (
      _id: EventId
    , _objId: ObjectId
    , _coll: String
    , _type: String
    , _created: java.time.Instant
    , _prev: Option[EventId] = None
  )

  implicit def eventIdFmt: Format[EventId]
  implicit def objectIdFmt: Format[ObjectId]

  implicit def eventDocFmt = Json.format[EventDoc[EventId, ObjectId]]

  implicit def format: OFormat[D]
  implicit def esdFormat: OFormat[EventSourcedDoc]

  protected case class EventSourcedDoc(_cur: EventId, _init: D, _deleted: Option[Instant] = None)
}

trait ReactiveMongoObject[ObjectId, EventId, A <: EventSourced[A, EventId], D <: BaseDoc[ObjectId]]
  extends ObjectDescriptor[ObjectId, EventId, A, D] {

  implicit def ec: ExecutionContext
  def id: ObjectId = underlying.fold(e => e.id, d => d._id)
  protected def underlying: Either[Empty[ObjectId], D]
  protected lazy val obj: D = underlying.right.getOrElse(throw new ObjectDeleted(id, collectionName))

  protected lazy val esdOptFt: Future[Option[EventSourcedDoc]] =
    collectionFt.flatMap(_.find(Json.obj("_id" -> Json.toJson(id)), projection = Some(Json.obj())).one[EventSourcedDoc])

  def initialState: Future[A] = esdOptFt.map(esdOpt => cons(Right(esdOpt.get._init)))

  def update[E <: A#AllowedEvent : OWrites](obj: D, event: E, parent: Option[BaseEvent[EventId]]): Future[A] =
     update(MongoObject.SetOp(Json.toJsObject(obj)), event, parent)

  def update[E <: A#AllowedEvent : OWrites](update: MongoObject.SetOp, event: E, parent: Option[BaseEvent[EventId]]): Future[A] =
    updateVerbose(Json.obj("$set" -> update.json), event, parent)

  def updateVerbose[E <: A#AllowedEvent : OWrites](update: JsObject, event: E, parent: Option[BaseEvent[EventId]],
                                         discriminator: JsObject = Json.obj("_id" -> Json.toJson(id))): Future[A] = {
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
        _ <- recordEvent(event, parent)
        objColl <- context.collectionFt(collectionName)
        esdOpt <- esdOptFt
        updateResult <- objColl.update(ordered=false).one(discriminator, getUpdateJson(esdOpt, event.generatedId)).map {
          case r if r.nModified == 0 => throw new ObjectUpdateNotApplied(this, discriminator)
          case r => r
        }
        result <- objColl.find(Json.obj("_id" -> Json.toJson(id)), projection = Some(Json.obj())).one[D].map(newObj => cons(Right(newObj.get)))
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

  protected def recordEvent[E <: BaseEvent[EventId] : OWrites](event: E, parent: Option[BaseEvent[EventId]]): Future[WriteResult] = {
    for {
      evtColl <- context.collectionFt(evtCollectionName)
      esdOpt <- esdOptFt
      result <- evtColl.insert(ordered=false).one(
        Json.toJsObject(
          EventDoc(
            _id = event.generatedId,
            _objId = id,
            _coll = collectionName,
            _type = event.getClass.getName,
            _created = Instant.now,
            _prev = esdOpt.map(_._cur)
          )
        ) ++ Json.toJsObject(event)
          ++ JsObject(parent.map(evt => "_parentEventId" -> Json.toJson(evt.generatedId)).toSeq)
      )
    } yield result
  }

  def delete[E <: BaseEvent[EventId] : OWrites](obj: D, event: E, parent: Option[BaseEvent[EventId]]): Future[A] = {
    for {
      objColl <- collectionFt
      evtColl <- context.collectionFt(evtCollectionName)
      esdOpt <- esdOptFt
      _ <- objColl.update(ordered=false).one(Json.obj("_id" -> Json.toJson(id)),
          esdOpt.map(_.copy(_cur = event.generatedId, _deleted = Some(Instant.now))).get
        )
      _ <- evtColl.insert(ordered=false).one(
          Json.toJsObject(
            EventDoc(
              _id = event.generatedId,
              _objId = id,
              _coll = collectionName,
              _type = event.getClass.getName,
              _created = Instant.now,
              _prev = esdOpt.map(_._cur)
            )
          ) ++ Json.toJsObject(event)
            ++ JsObject(parent.map(evt => "_parentEventId" -> Json.toJson(evt.generatedId)).toSeq)
        )
    } yield cons(Left(Empty(id)))
  }

  protected def purge[E <: BaseEvent[EventId] : OWrites](event: E, parent: Option[BaseEvent[EventId]]): Future[A] = {
    for {
      coll <- collectionFt
      evtColl <- context.collectionFt(evtCollectionName)
      esdOpt <- esdOptFt
      result <- coll.findAndRemove(
          selector = Json.obj("_id" -> Json.toJson(id))
        , sort = None
        , fields = None
        , writeConcern = WriteConcern.Acknowledged
        , maxTime = None
        , collation = None
        , arrayFilters = Seq.empty
      )
      _ <- evtColl.insert(ordered=false).one(
        Json.toJsObject(
          EventDoc(
            _id = event.generatedId,
            _objId = id,
            _coll = collectionName,
            _type = event.getClass.getName,
            _created = Instant.now,
            _prev = esdOpt.map(_._cur)
          )
        ) ++ Json.toJsObject(event)
          ++ JsObject(parent.map(evt => "_parentEventId" -> Json.toJson(evt.generatedId)).toSeq)
      )
    } yield cons(Left(Empty(id)))
  }
}

object MongoObject {
  /**
    * Wrapper that adds type safety to a mongo \$set operation.  We need to protect (at compile time) against invoking
    * non-\$set operations with the update(json) method, the problem being that such operations will silently fail when
    * run against mongo.
    * @param json json object that contains the field/value pairs to be set in the \$set op.
    */
  case class SetOp(json: JsObject)
}
