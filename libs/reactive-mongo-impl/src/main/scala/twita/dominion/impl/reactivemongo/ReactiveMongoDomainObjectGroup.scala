package twita.dominion.impl.reactivemongo

import java.time.Instant

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.Cursor
import reactivemongo.api.ReadConcern
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.compat._
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject
import twita.dominion.api.DomainObjectGroup
import twita.dominion.api.ex.ObjectDoesNotMeetConstraints

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

abstract class ReactiveMongoDomainObjectGroup[
  EventId: Format,
  A <: DomainObject[EventId, A],
  D <: BaseDoc[A#ObjectId]: OFormat
](implicit oidFormat: Format[A#ObjectId], executionContext: ExecutionContext)
    extends ObjectDescriptor[EventId, A, D]
    with DomainObjectGroup[EventId, A] {

  /**
    * Hard-coded into this implementation of ReactiveMongo domain objects is the idea that we will do soft
    * deletes when calling the delete() method on the domain object.  This means that when we are selecting
    * objects that are part of this group, we have to be sure to exclude the ones that have been deleted.
    */
  protected val notDeletedConstraint =
    Json.obj("_deleted" -> Json.obj("$exists" -> false))

  /**
    * Convenience method for building a simple criteria for pulling an object by id.
    * @param id The id of the object you are looking for
    * @return The JsObject that can be used by Mongo to fetch this object.
    */
  protected def byIdConstraint(id: A#ObjectId) =
    Json.obj("_id" -> Json.toJson(id))

  /**
    * Default implementation of the get method, which provides the most obvious query that will be useful
    * for all Domain Object groups.  Child classes to this one should override this method if they have more
    * details queries they need to handle, but by default they should call super.get(q) to make sure they
    * are able to take advantage of this default.
    *
    * @param q A query case class that the implementer of the DomainObjectGroup knows how to handle
    * @return Eventually, an optional A if is found, or None if no objects meet the criteria
    */
  override def get(q: DomainObjectGroup.Query): Future[Option[A]] = q match {
    case q: DomainObjectGroup.byId[A#ObjectId] =>
      getByJsonCrit(byIdConstraint(q.id))
  }

  /**
    * Convenience method for pulling a single object by id.
    * @param q byId query instance
    * @return Eventually, an optional A if is found, or None if no objects meet the criteria
    */
  protected def getById(
    q: DomainObjectGroup.byId[A#ObjectId]
  ): Future[Option[A]] = {
    getByJsonCrit(Json.obj("_id" -> q.id))
  }

  /**
    * Uses an arbitrary user constraint to build up a Mongo query, and runs that query against this Object Group.
    * @param userConstraint An arbitrary Mongo query (in the form of a JSON object) to be used to constrain the
    *                       result.
    *
    * @return Future that will be completed by Some(A) if a result is found, or None if no objects meet the
    *         criteria.  Note also that this method will do an additional check if it doesn't find any items
    *         that meet the constraint.  The provided {{userConstraint}} will be combined with the
    *         {{listConstraint}} for this group and that query will be used to find the result.  If none
    *         is found then we try another query without the additional {{listConstraint}}.  If that query
    *         returns Some, it means that there is an object in the system that meets the {{userConstraint}}
    *         but that the addition of the {{listConstraint}} excluded that object from this Object Group's
    *         {{listConstraint}}. If this is the case, we fail the Future with an
    *         {{ObjectDoesNotMeetConstraints}} exception.
    */
  protected def getByJsonCrit(userConstraint: JsObject): Future[Option[A]] = {
    getByJsonCritNoListConstraint(userConstraint ++ listConstraint).flatMap {
      case s: Some[_] => Future.successful(s)
      case None =>
        getByJsonCritNoListConstraint(userConstraint).flatMap {
          case Some(s) =>
            Future.failed(
              new ObjectDoesNotMeetConstraints(userConstraint, listConstraint)
            )
          case None => Future.successful(None)
        }
    }
  }

  /**
    * @param constraint The Mongo query (JSON object) to be used to find an object in the underlying Mongo
    *                   Collection.  Not that this will not also apply the {{listConstraint}} defined for
    *                   this object group.
    * @return Eventually, an optional A if is found, or None if no objects meet the criteria
    */
  protected def getByJsonCritNoListConstraint(
    constraint: JsObject
  ): Future[Option[A]] = {
    for {
      coll <- objCollectionFt
      docOpt <- coll
        .find(constraint ++ notDeletedConstraint, projection = Some(Json.obj()))
        .one[D]
    } yield docOpt.map(doc => cons(Right(doc)))
  }

  /**
    * @param constraint A Mongo query (JSON object) to be used to retrieve a list of A's from the database.
    * @param sort The sort order for the list.
    * @param offset How many items to skip (in case there is some paging going on here).
    * @param limit How many items to return in your set (default is no limit)
    * @return Eventually, a list of A's that meet your criteria and the {{listConstraint}} that is part of this
    *         Domain Object Group.
    */
  protected def getListByJsonCrit(constraint: JsObject,
                                  sort: JsObject = defaultOrder,
                                  offset: Option[Int] = None,
                                  limit: Int = -1 /* no limit */
  ): Future[List[A]] = {
    def buildQuery(coll: JSONCollection) = {
      val listCrit = constraint ++ listConstraint
      coll
        .find(listCrit ++ notDeletedConstraint, projection = Some(Json.obj()))
        .sort(sort)
        .skip(offset.getOrElse(0))
    }

    for {
      coll <- objCollectionFt
      docList <- buildQuery(coll)
        .cursor[D]()
        .collect[List](limit, Cursor.FailOnError[List[D]]())
    } yield docList.map(doc => cons(Right(doc)))
  }

  protected def defaultOrder = Json.obj()

  protected def listConstraint: JsObject

  override def list(): Future[List[A]] =
    for {
      coll <- objCollectionFt
      docList <- coll
        .find(
          listConstraint ++ notDeletedConstraint,
          projection = Some(Json.obj())
        )
        .sort(defaultOrder)
        .cursor[D]()
        .collect[List](-1, Cursor.FailOnError[List[D]]())
    } yield docList.map(doc => cons(Right(doc)))

  def count(): Future[Long] =
    for {
      coll <- objCollectionFt
      count <- coll.count(
        selector = Some(listConstraint ++ notDeletedConstraint),
        limit = Some(0),
        skip = 0,
        hint = None,
        readConcern = ReadConcern.Available
      )
    } yield count

  def source()(implicit m: Materializer): Future[Source[A, Any]] =
    source(listConstraint)

  def source(q: JsObject)(implicit m: Materializer): Future[Source[A, Any]] =
    for {
      coll <- objCollectionFt
    } yield {
      coll
        .find(q ++ notDeletedConstraint, projection = Some(Json.obj()))
        .sort(defaultOrder)
        .cursor[D]()
        .documentSource()
        .map(doc => cons(Right(doc)))
    }

  protected def create[E <: AllowedEvent](
    obj: D,
    event: E,
    parent: Option[BaseEvent[EventId]]
  )(implicit writes: OWrites[E]): Future[A] = {
    for {
      objColl <- objCollectionFt
      evtDoc = EventMetaData(
        event.generatedId,
        obj._id,
        objColl.name,
        event.getClass.getName,
        Instant.now
      )
      objWriteResult <- objColl
        .insert(ordered = false)
        .one(
          Json.toJsObject(obj) ++ Json
            .toJsObject(EventSourcedDoc(evtDoc._id, obj))
        )
      evtLogResult <- eventLogger.log(evtDoc, event, parent)
    } yield cons(Right(obj))
  }

  protected def upsert[E <: AllowedEvent](
    obj: D,
    event: E,
    parent: Option[BaseEvent[EventId]]
  )(implicit writes: OWrites[E]): Future[A] = {
    for {
      objColl <- objCollectionFt
      evtDoc = EventMetaData(
        event.generatedId,
        obj._id,
        objColl.name,
        event.getClass.getName,
        Instant.now
      )
      objWriteResult <- objColl
        .update(ordered = false)
        .one(
          Json.obj("_id" -> Json.toJson(obj._id)),
          Json.obj(
            "$set" -> (Json.toJsObject(obj) ++ Json
              .toJsObject(EventSourcedDoc(evtDoc._id, obj)))
          ),
          upsert = true
        )
      evtLogResult <- eventLogger.log(evtDoc, event, parent)
    } yield cons(Right(obj))
  }
}
