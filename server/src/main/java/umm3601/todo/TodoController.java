package umm3601.todo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;


import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;


import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import umm3601.Controller;

public class TodoController implements Controller {

  private static final String API_TODOS = "/api/todos";
  private static final String API_TODO_BY_ID = "/api/todos/{id}";
  static final String STATUS_KEY = "status";
  static final String OWNER_KEY = "owner";
  static final String BODY_KEY = "body";
  static final String CATEGORY_KEY = "category";

  private static final int REASONABLE_AGE_LIMIT = 150;
  private static final String CATEGORY_REGEX = "^(homework|software design|video games|groceries)$";

  private final JacksonMongoCollection<Todo> todoCollection;

  public TodoController(MongoDatabase database) {
    todoCollection = JacksonMongoCollection.builder().build(
      database, "todos", Todo.class, UuidRepresentation.STANDARD);
  }

  public void getTodo(Context ctx) {
    String id = ctx.pathParam("id");
    Todo todo;
    try {
      todo = todoCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested todo id wasn't a legal Mongo Object ID.");
    }
    if (todo == null) {
      throw new NotFoundResponse("The requested todo was not found");
    }

    ctx.json(todo);
    ctx.status(HttpStatus.OK);
  }

  public void getTodos(Context ctx) {
    Bson filter = constructFilter(ctx);
    Bson sort = Sorts.ascending("_id");

    String limitParam = ctx.queryParam("limit");
    int limit = (limitParam != null) ? Integer.parseInt(limitParam) : 0;

    List<Todo> todos = todoCollection
      .find(filter)
      .sort(sort)
      .limit(limit > 0 ? limit : 0)
      .into(new ArrayList<>());

    ctx.json(todos);
    ctx.status(HttpStatus.OK);
  }

  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>();


    if (ctx.queryParamMap().containsKey(OWNER_KEY)) {
      String owner = ctx.queryParam(OWNER_KEY);
      filters.add(regex(OWNER_KEY, Pattern.compile(Pattern.quote(owner), Pattern.CASE_INSENSITIVE)));
    }

    if (ctx.queryParamMap().containsKey(CATEGORY_KEY)) {
      String category = ctx.queryParamAsClass(CATEGORY_KEY, String.class)
        .check(r -> r.matches(CATEGORY_REGEX), "Invalid todo category")
        .get();
      filters.add(eq(CATEGORY_KEY, category));
    }

    if (ctx.queryParamMap().containsKey("status")) {
      String status = ctx.queryParam("status");
      if (status != null) {
        filters.add(eq("completed", status.equalsIgnoreCase("complete")));
      }
    }

    if (ctx.queryParamMap().containsKey("contains")) {
      String contains = ctx.queryParam("contains");
      if (contains != null && !contains.isEmpty()) {
        filters.add(regex("body", Pattern.compile(Pattern.quote(contains), Pattern.CASE_INSENSITIVE)));
      }
    }

    return filters.isEmpty() ? new Document() : and(filters);
  }




  @Override
  public void addRoutes(Javalin server) {
    server.get(API_TODO_BY_ID, this::getTodo);
    server.get(API_TODOS, this::getTodos);

  }

  JacksonMongoCollection<Todo> getTodoCollection() {
    return todoCollection;
  }
}
