package twita.bearch.domain

package object api {
  trait DomainObject[EventId, A <: HasId] extends EventSourced[A, EventId] with HasId
}
