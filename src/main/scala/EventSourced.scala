package twita.bearch

import java.util.UUID
import scala.concurrent.Future

/**
 * Domain objects to which events may be applied are said to be "event sourced".  This base trait defines the
 * type member from which any of the events that may be applied to this object should extend.
 *
 * @tparam A The object that is to receive events
 */
trait EventSourced[A, EventId] {
  /**
   * This type member should be set to the base trait that from which all events that are to be applied to this
   * object are extended.  The {{{apply()}}} method will then be able to exhaustively match for the correct
   * event to handle.
   */
  type AllowedEvent <: BaseEvent[EventId]

  def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]] = None): Future[A]

  protected def evtCollectionName: String
}

trait EventHistory[A] {
  /**
    * @return A Future of the initial state of this event sourced object.
    */
  def initialState: Future[A]
}

/**
  * All of the EventSourced objects in the domain will define a sealed set of events that may be applied
  * to that particular object.  All of those sealed traits should extend from this base trait.  These events are
  * stored as part of an event stream which can be replayed from an initial object state in order to restore a
  * particular domain object to any state that it has ever been in.
  */
trait BaseEvent[EventId] {
  /**
    * Events have an inherent ability to generate an id field.  This is important for creation events, where
    * the application issuing the event does not yet have an id because one does not yet exist for the new object.
    * In the implementation of the object creation, the code should delegate the creation of a new id to this
    * method which will be available on every event in the system.
    */
  def generateId: EventId

  lazy val generatedId: EventId = generateId
}
