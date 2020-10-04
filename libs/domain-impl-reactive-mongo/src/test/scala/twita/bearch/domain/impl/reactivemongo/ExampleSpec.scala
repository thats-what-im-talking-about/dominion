package twita.bearch.domain.impl.reactivemongo

import java.util.UUID

import org.scalatest._
import org.scalatest.flatspec._
import org.scalatest.matchers._
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import reactivemongo.play.json.collection.JSONCollection
import twita.bearch.domain.api.BaseEvent
import twita.bearch.domain.api.DomainObject
import twita.bearch.domain.api.DomainObjectGroup
import twita.bearch.domain.api.EmptyEventFmt
import twita.bearch.domain.api.IdGenerator

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class EventId(value: String) extends AnyVal
object EventId {
  implicit val fmt = new Format[EventId] {
    override def reads(json: JsValue): JsResult[EventId] = json match {
      case JsString(id) => JsSuccess(EventId(id))
      case err => JsError(s"Expected a String but got ${err}")
    }

    override def writes(o: EventId): JsValue = JsString(o.value)
  }
}

trait EventIdGenerator extends IdGenerator[EventId] {
  override def generateId: EventId = EventId(UUID.randomUUID().toString)
}

case class TestId(value: String) extends AnyVal
object TestId {
  def apply(): TestId = TestId(UUID.randomUUID().toString)
  implicit val fmt = new Format[TestId] {
    override def reads(json: JsValue): JsResult[TestId] = json match {
      case JsString(id) => JsSuccess(TestId(id))
      case err => JsError(s"Expected a String but got ${err}")
    }

    override def writes(o: TestId): JsValue = JsString(o.value)
  }
}

// define a test domain object
trait Test extends DomainObject[EventId, Test]
{
  override type AllowedEvent = Test.Event
  override type ObjectId = TestId

  def name: String
  def version: Int
}

object Test {
  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator

  case class Deleted() extends Event
  object Deleted { implicit val fmt = EmptyEventFmt(Deleted()) }
}

trait Tests extends DomainObjectGroup[EventId, Test] {
  override type AllowedEvent = Tests.Event
}

object Tests {
  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator
  case class Created(name: String, version: Int) extends Event
  object Created { implicit val fmt = Json.format[Created]}
}

// define a test mongo doc format
case class TestDoc(
    _id: TestId
  , name: String
  , version: Int
) extends BaseDoc[TestId]
object TestDoc { implicit val fmt = Json.format[TestDoc] }

trait TestDescriptor extends ObjectDescriptor[EventId, Test, TestDoc] {
  implicit def executionContext: ExecutionContext
  implicit def mongoContext: MongoContext

  override protected def objCollectionFt: Future[JSONCollection] = mongoContext.getCollection("tests")
  override protected def cons: Either[Empty[TestId], TestDoc] => Test = o => new MongoTest(o)

  override def eventLogger: EventLogger = new MongoEventLogger {
    override protected def evtCollectionFt: Future[JSONCollection] = mongoContext.getCollection("tests.events")
  }
}

class MongoTest(protected val underlying: Either[Empty[TestId], TestDoc])(
  implicit val executionContext: ExecutionContext, val mongoContext: MongoContext
) extends ReactiveMongoObject[EventId, Test, TestDoc]
    with TestDescriptor
    with Test
{
  override def name: String = obj.name
  override def version: Int = obj.version
  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[Test] = event match {
    case d: Test.Deleted => delete(obj, d, parent)
  }
}

class MongoTests(implicit val executionContext: ExecutionContext, val mongoContext: MongoContext)
  extends ReactiveMongoDomainObjectGroup[EventId, Test, TestDoc]
    with TestDescriptor
    with Tests
{
  override protected def listConstraint: JsObject = Json.obj()
  override type AllowedEvent = Tests.Event
  override def list(q: DomainObjectGroup.Query): Future[List[Test]] = ???

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[Test] = event match {
    case evt: Tests.Created => create(TestDoc(TestId(), evt.name, evt.version), evt, parent)
  }
}


class ExampleSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new MongoContextImpl

  "objectGroup.create" should "insert a new object" in {
    val testGroup = new MongoTests
    for {
      newGroup <- testGroup(Tests.Created("foo", 2))
      confirmGroup <- testGroup.get(DomainObjectGroup.byId(newGroup.id))
    } yield assert(confirmGroup.exists(_.name == newGroup.name))
  }

  "objectGroup.delete" should "delete an object" in {
    val context = new MongoContextImpl
    val testGroup = new MongoTests
    for {
      tests <- testGroup.list()
      test = tests.head
      result <- test(Test.Deleted())
      check <- testGroup.get(DomainObjectGroup.byId(test.id))
    } yield assert(!check.isDefined)
  }
}

