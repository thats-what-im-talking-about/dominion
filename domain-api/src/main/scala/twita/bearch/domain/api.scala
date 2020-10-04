package twita.bearch.domain

package object api {

  /**
    * Convenience trait that connects the EventSourced trait together with the HasId trait.  The idea was just that
    * it looks nicer to have
    * <pre>
    *   trait User extends DomainObject[EventId, User]
    * </pre>
    * than it does to have
    * <pre>
    *   trait User extends EventSources[User, EventId] with HasId
    * </pre>
    *
    * @tparam EventId the arbitrary type that will be used to uniquely identify events that have been applied.
    * @tparam A The domain object type
    */
  trait DomainObject[EventId, A <: HasId] extends EventSourced[A, EventId] with HasId
}
