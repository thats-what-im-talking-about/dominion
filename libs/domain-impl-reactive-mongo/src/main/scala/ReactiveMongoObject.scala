package twita.bearch.domain.impl.reactivemongo

import java.time.Instant

import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.compat._
import play.api.libs.json.Format
import play.api.libs.json.JsDefined
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import reactivemongo.api.Cursor
import reactivemongo.api.MongoConnection
import reactivemongo.api.ReadConcern
import reactivemongo.api.WriteConcern
import reactivemongo.api.commands.WriteResult
import twita.bearch.domain.api.BaseEvent
import twita.bearch.domain.api.Context
import twita.bearch.domain.api.DomainObject
import twita.bearch.domain.api.EventSourced
import twita.bearch.domain.api.HasId
import twita.bearch.domain.api.DomainObjectGroup
import twita.bearch.domain.api.ex.ObjectDeleted
import twita.bearch.domain.api.ex.ObjectDoesNotMeetConstraints
import twita.bearch.domain.api.ex.ObjectUpdateNotApplied

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait MongoContext extends Context {
  def getCollection(name: String): Future[JSONCollection]
}

trait BaseDoc[ObjectId] {
  def _id: ObjectId
}

case class Empty[ObjectId](id: ObjectId)

abstract class ObjectDescriptor[
    EventId: Format
  , A <: EventSourced[A, EventId] with HasId
  , D <: BaseDoc[A#ObjectId]: OFormat
](protected val context: MongoContext)(implicit oidFormat: Format[A#ObjectId], executionContext: ExecutionContext) {
  protected def objCollectionFt: Future[JSONCollection]
  protected def evtCollectionFt: Future[JSONCollection]

  protected def cons: Either[Empty[A#ObjectId], D] => A

  case class EventDoc (
      _id: EventId
    , _objId: A#ObjectId
    , _coll: String
    , _type: String
    , _created: java.time.Instant
    , _prev: Option[EventId] = None
  )
  object EventDoc { implicit def fmt = Json.format[EventDoc] }

  protected case class EventSourcedDoc(_cur: EventId, _init: D, _deleted: Option[Instant] = None)
  object EventSourcedDoc { implicit def fmt = Json.format[EventSourcedDoc] }
}


abstract class ReactiveMongoObject[EventId: Format, A <: EventSourced[A, EventId] with HasId, D <: BaseDoc[A#ObjectId]: OFormat](context: MongoContext)(
  implicit oidFormat: Format[A#ObjectId], executionContext: ExecutionContext
) extends ObjectDescriptor[EventId, A, D](context)
{
  def id: A#ObjectId = underlying.fold(e => e.id, d => d._id)
  protected def underlying: Either[Empty[A#ObjectId], D]
  protected lazy val obj: D = underlying.right.getOrElse(throw new ObjectDeleted(id))
  protected lazy val esdOptFt: Future[Option[EventSourcedDoc]] =
    objCollectionFt.flatMap(_.find(Json.obj("_id" -> Json.toJson(id)), projection = Some(Json.obj())).one[EventSourcedDoc])

  def initialState: Future[A] = esdOptFt.map(esdOpt => cons(Right(esdOpt.get._init)))

  def update[E <: A#AllowedEvent : OWrites](
      obj: D
    , event: E
    , parent: Option[BaseEvent[EventId]]
  ): Future[A] = update(ReactiveMongoObject.SetOp(Json.toJsObject(obj)), event, parent)

  def update[E <: A#AllowedEvent : OWrites](
      update: ReactiveMongoObject.SetOp
    , event: E
    , parent: Option[BaseEvent[EventId]]
  ): Future[A] = updateVerbose(Json.obj("$set" -> update.json), event, parent)

  def updateVerbose[E <: A#AllowedEvent : OWrites](
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
        _ <- recordEvent(event, parent)
        objColl <- objCollectionFt
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
      objColl <- objCollectionFt
      evtColl <- evtCollectionFt
      esdOpt <- esdOptFt
      result <- evtColl.insert(ordered=false).one(
        Json.toJsObject(
          EventDoc(
            _id = event.generatedId,
            _objId = id,
            _coll = objColl.name,
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
      objColl <- objCollectionFt
      evtColl <- evtCollectionFt
      esdOpt <- esdOptFt
      _ <- objColl.update(ordered=false).one(Json.obj("_id" -> Json.toJson(id)),
          esdOpt.map(_.copy(_cur = event.generatedId, _deleted = Some(Instant.now))).get
        )
      _ <- evtColl.insert(ordered=false).one(
          Json.toJsObject(
            EventDoc(
              _id = event.generatedId,
              _objId = id,
              _coll = objColl.name,
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
      objColl <- objCollectionFt
      evtColl <- evtCollectionFt
      esdOpt <- esdOptFt
      result <- objColl.findAndRemove(
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
            _coll = objColl.name,
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

object ReactiveMongoObject {
  /**
    * Wrapper that adds type safety to a mongo \$set operation.  We need to protect (at compile time) against invoking
    * non-\$set operations with the update(json) method, the problem being that such operations will silently fail when
    * run against mongo.
    * @param json json object that contains the field/value pairs to be set in the \$set op.
    */
  case class SetOp(json: JsObject)
}

abstract class ReactiveMongoDomainObjectGroup[
    EventId: Format
  , A <: DomainObject[EventId, A]
  , D <: BaseDoc[A#ObjectId]: OFormat
](context: MongoContext)(implicit oidFormat: Format[A#ObjectId], executionContext: ExecutionContext)
extends ObjectDescriptor[EventId, A, D](context) with DomainObjectGroup[EventId, A]
{
  protected val notDeletedConstraint = Json.obj("_deleted" -> Json.obj("$exists" -> false))

  protected def byIdConstraint(id: A#ObjectId) = Json.obj("_id" -> Json.toJson(id))

  override def get(q: DomainObjectGroup.Query): Future[Option[A]] = q match {
    case q: DomainObjectGroup.byId[A#ObjectId] => getByJsonCrit(byIdConstraint(q.id))
  }

  protected def getById(q: DomainObjectGroup.byId[A#ObjectId]): Future[Option[A]] = {
    getByJsonCrit(Json.obj("_id" -> q.id))
  }

  protected def getByJsonCrit(json: JsObject): Future[Option[A]] = {
    getByJsonCritNoListConstraint(json ++ listConstraint).flatMap {
      case s: Some[_] => Future.successful(s)
      case None => getByJsonCritNoListConstraint(json).flatMap {
        case Some(s) => Future.failed(new ObjectDoesNotMeetConstraints(json, listConstraint))
        case None => Future.successful(None)
      }
    }
  }

  protected def getByJsonCritNoListConstraint(json: JsObject): Future[Option[A]] = {
    for {
      coll <- objCollectionFt
      docOpt <- coll.find(json ++ notDeletedConstraint, projection = Some(Json.obj())).one[D]
    } yield docOpt.map(doc => cons(Right(doc)))
  }

  protected def getListByJsonCrit( crit: JsObject
                                 , sort: JsObject = defaultOrder
                                 , offset: Option[Int] = None
                                 , limit: Int = -1 /* no limit */): Future[List[A]] = {
    def buildQuery(coll: JSONCollection) = {
      val listCrit = crit ++ listConstraint
      coll.find(listCrit ++ notDeletedConstraint, projection = Some(Json.obj())).sort(sort).skip(offset.getOrElse(0))
    }

    for {
      coll <- objCollectionFt
      docList <- buildQuery(coll).cursor[D]().collect[List](limit, Cursor.FailOnError[List[D]]())
    } yield docList.map(doc => cons(Right(doc)))
  }

  protected def defaultOrder = Json.obj()

  protected def listConstraint: JsObject

  override def list(): Future[List[A]] = for {
    coll <- objCollectionFt
    docList <- coll.find(listConstraint ++ notDeletedConstraint, projection = Some(Json.obj()))
                .sort(defaultOrder).cursor[D]()
                .collect[List](-1, Cursor.FailOnError[List[D]]())
  } yield docList.map(doc => cons(Right(doc)))

  def count(): Future[Long] = for {
    coll <- objCollectionFt
    count <- coll.count(
      selector = Some(listConstraint ++ notDeletedConstraint)
      , limit = Some(0)
      , skip = 0
      , hint = None
      , readConcern = ReadConcern.Available
    )
  } yield count

  protected def create[E <: BaseEvent[EventId]](obj: D, event: E, parent: Option[BaseEvent[EventId]])(implicit writes: OWrites[E]): Future[A] = {
    for {
      objColl <- objCollectionFt
      evt = EventDoc(event.generatedId, obj._id, objColl.name, event.getClass.getName, Instant.now)
      objWriter = implicitly[OWrites[D]]
      esdWriter = implicitly[OWrites[EventSourcedDoc]]
      evtColl <- evtCollectionFt
      objWriteResult <- objColl.insert(ordered=false).one(Json.toJsObject(obj) ++ Json.toJsObject(EventSourcedDoc(evt._id, obj)))
      evtWriteResult <- evtColl.insert(ordered=false).one(Json.toJsObject(evt) ++ Json.toJsObject(event)
                          ++ JsObject(parent.map(evt => "_parentEventId" -> Json.toJson(evt.generatedId)).toSeq))
    } yield cons(Right(obj))
  }

  protected def upsert[E <: BaseEvent[EventId]](obj: D, event: E, parent: Option[BaseEvent[EventId]])(implicit writes: OWrites[E]): Future[A] = {
    for {
      objColl <- objCollectionFt
      evt = EventDoc(event.generatedId, obj._id, objColl.name, event.getClass.getName, Instant.now)
      evtColl <- evtCollectionFt
      objWriteResult <- objColl.update(ordered=false).one(Json.obj("_id" -> Json.toJson(obj._id)), Json.obj("$set" -> (Json.toJsObject(obj) ++ Json.toJsObject(EventSourcedDoc(evt._id, obj)))), upsert = true)
      evtWriteResult <- evtColl.insert(ordered=false).one(Json.toJsObject(evt) ++ Json.toJsObject(event)
                          ++ JsObject(parent.map(evt => "_parentEventId" -> Json.toJson(evt.generatedId)).toSeq))
    } yield cons(Right(obj))
  }
}
