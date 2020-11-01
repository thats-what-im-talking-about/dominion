package twita.dominion.api

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import scala.concurrent.Future

/**
  * An ObjectGroup represents a group of objects in the domain model.  This is the base trait that is used
  * to describe a 1-many relationship in the domain.  It is also EventSourced which means that events
  * (typically adding or upserting new instances) may be applied to this ObjectGroup.
  */
trait DomainObjectGroup[EventId, A <: HasId] extends EventSourced[A, EventId] {
  /**
    * Simple method for finding a single instance of a domain object given some query criteria.
    * @param q A query case class that the implementer of the DomainObjectGroup knows how to handle
    * @return Eventually, an optional A if is found, or None if no objects meet the criteria
    */
  def get(q: DomainObjectGroup.Query): Future[Option[A]]

  /**
    * This method is used to get the list of domain objects that are in this particular group.  This is
    * not supposed to be a simple "SELECT * FROM table" query though; the idea is that a domain object
    * group can be defined as a property of another domain object, and the underlying implemenetation
    * is able to provide a constraint (or, in our SELECT * FROM table example, a WHERE clause) to limint
    * the amount of items in this list.  Note that if the data set gets large, this method will return
    * the entire materialized list of items which could get big.
    *
    * @return Eventually returns the list of fully instantiated A's
    */
  def list: Future[List[A]]

  def list(q: DomainObjectGroup.Query): Future[List[A]]

  def source()(implicit m: Materializer): Future[Source[A, Any]]

  /**
    * Same as the {{list}} method, this method is here to provide a way to just find the number of items in
    * this (presumably constrained) list.
    *
    * @return
    */
  def count: Future[Long]
}

object DomainObjectGroup {
  /**
    * Marker interface for indicating that a given case class is a Query.
    */
  trait Query

  /**
    * Generic byId query that is used to fetch a particular object by unique id from the ObjectGroup.
    *
    * @param id The id to be queried
    * @tparam ObjectId The type of the id for this particular Domain Object
    */
  case class byId[ObjectId](id: ObjectId) extends Query
}
