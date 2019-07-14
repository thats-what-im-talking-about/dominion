package twita.bearch.domain.api

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
