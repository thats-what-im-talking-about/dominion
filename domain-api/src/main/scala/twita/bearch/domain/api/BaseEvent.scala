package twita.bearch.domain.api

/**
  * All of the EventSourced objects in the domain will define a sealed set of events that may be applied
  * to that particular object.  All of those sealed traits should extend from this base trait.  These events are
  * stored as part of an event stream which can be replayed from an initial object state in order to restore a
  * particular domain object to any state that it has ever been in.
  */
trait BaseEvent[EventId] extends IdGenerator[EventId] {
  lazy val generatedId: EventId = generateId
}

trait IdGenerator[EventId] {
  def generateId: EventId
}
