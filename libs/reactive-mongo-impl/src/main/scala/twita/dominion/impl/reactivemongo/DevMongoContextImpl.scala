package twita.dominion.impl.reactivemongo

import reactivemongo.api.MongoConnection
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * Extremely simple MongoContext that puts all collections into a database called `dev` that lives on `localhost`
  */
class DevMongoContextImpl(implicit executionContext: ExecutionContext) extends MongoContext {
  def collLookup(str: String) = "dev"

  override def getCollection(name: String): Future[JSONCollection] = {
    for {
      conn <- dbConn
      db <- conn.database(collLookup(name))
    } yield db.collection(name)
  }

  val driver = new reactivemongo.api.AsyncDriver
  val dbConn: Future[MongoConnection] = driver.connect(List("localhost"))
}
