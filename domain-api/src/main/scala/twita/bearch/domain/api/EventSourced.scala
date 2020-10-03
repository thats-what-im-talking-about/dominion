package twita.bearch.domain.api

import scala.concurrent.Future

trait EventSourced[A <: HasId, EventId] {
  type AllowedEvent <: BaseEvent[EventId]
  def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]] = None): Future[A]
}

trait HasId {
  type ObjectId
  def id: ObjectId
}
