package controllers;

import actors.*;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.fasterxml.jackson.databind.JsonNode;
import play.api.*;
import play.api.mvc.*;
import play.libs.Akka;
import play.libs.F;
import play.mvc.WebSocket;
import play.mvc.Controller;
import play.mvc.Result;
import scala.Option;

public class Application extends Controller {

  public Result index() {
    return ok(views.html.index.render());
  }

  public WebSocket<JsonNode> ws() {
    return new WebSocket<JsonNode>() {
      @Override
      public void onReady(final WebSocket.In<JsonNode> in, final WebSocket.Out<JsonNode> out) {
        //create a new UserActor and give it the default stocks watch
        final ActorRef userActor = Akka.system().actorOf(Props.create(UserActor.class , out));

        //send all WebSocket message to the UserActor
        in.onMessage(new F.Callback<JsonNode>() {
          @Override
          public void invoke(JsonNode jsonNode) throws Throwable {
            // parse the JSON into WatchStock
            WatchStock watchStock = new WatchStock(jsonNode.get("symbol").textValue());
            // send the watchStock message to the StockActor
            StockActors.stocksActor().tell(watchStock, userActor);
          }
        });

        // on close, tell the userActor to shutdown
        in.onClose(new F.Callback0(){
          @Override
          public void invoke() throws Throwable {
            final Option<String> none = Option.empty();
            StockActors.stocksActor().tell(new UnwatchSotck(none), userActor);
            Akka.system().stop(userActor);
          }
        });
      }
    };
  }
}