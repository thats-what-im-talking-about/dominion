package twita.dominion.impl.reactivemongo

import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * Simple context interface that applications should implement in order to give their Dominion object
  * implementations access to the actual underlying database.
  */
trait MongoContext {
  /**
    * @param name Name of the collection you're looking for.
    * @return eventually returns a JSONCollection interface that may be used to access your data.
    */
  def getCollection(name: String)(implicit executionContext: ExecutionContext): Future[JSONCollection]
}

