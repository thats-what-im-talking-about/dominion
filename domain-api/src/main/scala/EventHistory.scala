package twita.bearch

import scala.concurrent.Future

/**
  * Defines methods related to the history of this object.  The only mutations that are allowed to twita domain
  * objects are via the application of legal Events that are defined for each object type.  The initial state of
  * the object, along with all of the events that have been applied to the object, are stored in the backing
  * persistence mechanism.
  *
  * @tparam A The type of the domain object whose history we are tracking.
  */
trait EventHistory[A] {
  /**
    * @return A Future of the initial state of this event sourced object.
    */
  def initialState: Future[A]
}
