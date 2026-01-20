package umm3601.Todos;
import java.util.Arrays;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import org.bson.UuidRepresentation;

import io.javalin.Javalin;
import io.javalin.http.InternalServerErrorResponse;

public class TodosServer {

  private static  final int SERVER_PORT = 4567;

  private final MongoClient mongoClient;

  private Controller[] controllers;

public Server(MongoClient mongoClient, Controller[] controllers) {
  this.mongoClient = mongoClient;
  this.controllers = Arrays.copyOf(controllers, controllers.length);
}

static MongoClient configureDataBase(String mongoAddr) {
  MongoClient mongoClient = MongoClients.create(MongoClientSettings
    .builder()
    ,applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
    .uuidRepresentation(UuidRepresentation.STANDARD)
    ,build());
  return mongoClient;
}



}
