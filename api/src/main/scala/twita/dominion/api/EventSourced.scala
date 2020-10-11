package twita.dominion.api

import scala.concurrent.Future

/**
  * Simple trait that forces implementers to define a type for the events that may be applied to this object.
  *
  * @tparam A The domain object type that we are going to apply events to.
  * @tparam EventId The type that will be used as the unique identifier for the EventIds that are created and
  *                 applied to this EventSourced instance.
  */
trait EventSourced[A <: HasId, EventId] {

  /**
    * Implementers will need to provide an AllowedEvent type that is used to define the set of events that may be
    * legally applied to this object.  Generally the Event list is defined in a companion object as a sealed trait
    * so that the compiler is able to make sure that the apply() method (also defined here) checks for all possible
    * events that need to be handled.
    */
  type AllowedEvent <: BaseEvent[EventId]

  /**
    * All mutations to this trait will be applied to this object as an event, using this method.
    *
    * @param event The event that the application is applying to this domain object.
    * @param parent Sometimes the application of an event comes as a result of the application of another event
    *               to a different object.  If this happens, the parent event that brought about the application
    *               of this event should be passed in here as the "parent event" so that we are able to store
    *               that in the event log.
    * @return Eventually returns the state of A after the event has been applied.
    */
  def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]] = None): Future[A]
}

/**
  * Using this system, all domain objects will have an unique identifier of some arbitrary type.  We don't care
  * what that type is, but the implementer (type A) must define the type of its unique identifier by
  * overriding ObjectId.
  */
trait HasId {
  type ObjectId
  def id: ObjectId
}
