package twita.bearch.domain.api

import scala.concurrent.Future

/**
  * An ObjectGroup represents a group of objects in the domain model.  This is the base trait that is used
  * to describe a 1-many relationship in the domain.  It is also EventSourced which means that events
  * (typically adding new instances) may be applied to this ObjectGroup.
  */
trait ObjectGroup[A, ObjectId, EventId] extends EventSourced[A, EventId] {
  def get(q: ObjectGroup.Query): Future[Option[A]]

  def list(q: ObjectGroup.Query): Future[List[A]] = ???

  def list: Future[List[A]]

  def count: Future[Long]

  case class byId(id: ObjectId) extends ObjectGroup.Query
}

object ObjectGroup {
  trait Query
}
