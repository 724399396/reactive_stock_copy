package actors


import akka.actor.{Props, ActorRef, Actor}
import utils.{StockQuote, FakeStockQuote}
import java.util.Random
import scala.collection.immutable.{HashSet, Queue}
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import play.libs.Akka

/**
  * Created by weili on 16-4-9.
  */
class StockActor(symbol: String) extends Actor {
  lazy val stockQuote: StockQuote = new FakeStockQuote

  protected[this] var watchers: HashSet[ActorRef] = HashSet.empty[ActorRef]

  // A random data set which use s stockQuote.newPrice to get each data point
  var stockHistory: Queue[java.lang.Double] = {
    lazy val initPrices: Stream[java.lang.Double] = (new Random().nextDouble() * 800) #:: initPrices.map(previous => stockQuote.newPrice(previous))
    initPrices.take(50).to[Queue]
  }

  // Fetch the lasted stock value empty 75ms
  val stockTick = context.system.scheduler.schedule(Duration.Zero, 75.millis, self, FetchLatest)

  def receive = {
    case FetchLatest =>
      val newPrice = stockQuote.newPrice(stockHistory.last.doubleValue())
      stockHistory = stockHistory.drop(1) :+ newPrice
      watchers.foreach(_ ! StockUpdate(symbol, newPrice))
    case WatchStock(_) =>
      sender ! StockHistory(symbol, stockHistory.asJava)
      watchers = watchers + sender
    case UnwatchSotck(_) =>
      watchers -= sender
      if (watchers.size == 0) {
        stockTick.cancel()
        context.stop(self)
      }
  }
}

class StockActors extends Actor {
  def receive = {
    case watchStock @ WatchStock(symbol) =>
      context.child(symbol).getOrElse {
        context.actorOf(Props(new StockActor(symbol)), symbol)
      } forward watchStock
    case unwatchStock @ UnwatchSotck(Some(symbol)) =>
      context.child(symbol).foreach(_.forward(unwatchStock))
    case unwatchStock @ UnwatchSotck(none) =>
      context.children.foreach(_.forward(unwatchStock))
  }
}

object StockActors {
  lazy val stocksActor: ActorRef = Akka.system.actorOf(Props[StockActors])
}

case object FetchLatest

case class StockUpdate(symbol: String, price: Number)

case class StockHistory(symbol: String, history: java.util.List[java.lang.Double])

case class WatchStock(symbol: String)

case class UnwatchSotck(symbol: Option[String])
