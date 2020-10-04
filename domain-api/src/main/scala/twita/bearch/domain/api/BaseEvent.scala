package twita.bearch.domain.api

/**
  * All of the EventSourced objects in the domain will define a sealed set of events that may be applied
  * to that particular object.  All of those sealed traits should extend from this base trait.  These events are
  * stored as part of an event log which can be used to trace the state of a given object from its initial state
  * to the state that it is currently in.
  */
trait BaseEvent[EventId] extends IdGenerator[EventId] {
  lazy val generatedId: EventId = generateId
}

/**
  * Defines the interface contract for generating a new event id.  This has been split out in order to provide more
  * flexibility to applications in how they choose to generate new ids for the event log.
  *
  * @tparam EventId The type of the event id that is to be stored in the event log.
  */
trait IdGenerator[EventId] {
  def generateId: EventId
}
