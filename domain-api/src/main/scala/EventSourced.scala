package twita.bearch

import scala.concurrent.Future

/**
  * Domain objects to which events may be applied are said to be "event sourced".  This base trait defines the
  * type member from which any of the events that may be applied to this object should extend.
  *
  * https://martinfowler.com/eaaDev/EventSourcing.html
  *
  * @tparam A The object that is to receive events
  * @tparam EventId The type or the unique identifier for the events to be stored in the event stream.
  */
trait EventSourced[A, EventId] {
  /**
   * This type member should be set to the base trait that from which all events that are to be applied to this
   * object are extended.  The apply() method will then be able to exhaustively match for the correct
   * event to handle.
   */
  type AllowedEvent <: BaseEvent[EventId]

  def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]] = None): Future[A]
}
