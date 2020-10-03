package twita.bearch.domain.api

import scala.concurrent.Future

/**
  * An ObjectGroup represents a group of objects in the domain model.  This is the base trait that is used
  * to describe a 1-many relationship in the domain.  It is also EventSourced which means that events
  * (typically adding new instances) may be applied to this ObjectGroup.
  */
trait DomainObjectGroup[EventId, A <: HasId] extends EventSourced[A, EventId] {
  def get(q: DomainObjectGroup.Query): Future[Option[A]]

  def list(q: DomainObjectGroup.Query): Future[List[A]] = ???

  def list: Future[List[A]]

  def count: Future[Long]
}

object DomainObjectGroup {
  trait Query

  case class byId[ObjectId](id: ObjectId) extends Query
}
