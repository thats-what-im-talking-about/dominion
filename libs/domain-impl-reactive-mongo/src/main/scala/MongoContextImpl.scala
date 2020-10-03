package twita.bearch.domain.impl.reactivemongo

import reactivemongo.api.MongoConnection
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class MongoContextImpl(implicit executionContext: ExecutionContext) extends MongoContext {
  def collLookup(str: String) = "bearch"

  override def getCollection(name: String): Future[JSONCollection] = {
    for {
      conn <- dbConn
      db <- conn.database(collLookup(name))
    } yield db.collection(name)
  }

  val driver = new reactivemongo.api.AsyncDriver
  val dbConn: Future[MongoConnection] = driver.connect(List("localhost"))
}
