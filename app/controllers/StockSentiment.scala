package controllers

import play.api.Play
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.libs.ws.{WS, WSResponse}
import play.api.mvc.{Action, AnyContent, Controller}
import play.api.mvc.Result
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
  * Created by weili on 16-4-9.
  */
class StockSentiment extends Controller {

  case class Tweet(text: String)

  implicit val tweetReads = Json.reads[Tweet]

  def getTextSentiment(text: String): Future[WSResponse] =
    WS.url(Play.current.configuration.getString("sentiment.url").get) post Map("text" -> Seq(text))

  def getAverageSentiment(responses: Seq[WSResponse], label: String): Double = responses.map { response =>
    (response.json \\ label).head.as[Double]
  }.sum / responses.length.max(1) // avoid division by zero

  def loadSentimentFromTweets(json: JsValue): Seq[Future[WSResponse]] =
    (json \ "statuses").as[Seq[Tweet]] map (tweet => getTextSentiment(tweet.text))

  def getTweets(symbol: String): Future[WSResponse] = {
    WS.url(Play.current.configuration.getString("tweet.url").get.format(symbol)).get.withFilter {
      response => response.status == OK
    }
  }

  def sentimentJson(sentiments: Seq[WSResponse]) = {
    val neg = getAverageSentiment(sentiments, "neg")
    val neutral = getAverageSentiment(sentiments, "neutral")
    val pos = getAverageSentiment(sentiments, "pos")

    val response = Json.obj(
      "probability" -> Json.obj(
        "neg" -> neg,
        "neutral" -> neutral,
        "pos" -> pos
      )
    )

    val classification =
      if (neutral > 0.5)
        "neutral"
      else if (neg > pos)
        "neg"
      else
        "pos"

    response + ("label" -> JsString(classification))
  }

  def get(symbol: String): Action[AnyContent] = Action.async {
    val futureStockSentiments: Future[Result] = for {
      tweets <- getTweets(symbol)
      futureSentiments = loadSentimentFromTweets(tweets.json)
      sentiments <- Future.sequence(futureSentiments)
    } yield Ok(sentimentJson(sentiments))

    futureStockSentiments.recover {
      case nese: NoSuchElementException =>
        InternalServerError(Json.obj("error" -> JsString("Could not fetch the tweets")))
    }
  }
}
