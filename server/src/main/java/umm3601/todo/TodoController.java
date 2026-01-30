package umm3601.todo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.DeleteResult;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import umm3601.Controller;

public class TodoController implements Controller {

  private static final String API_TODOS = "/api/todos";
  private static final String API_TODO_BY_ID = "/api/todos/{id}";
  static final String AGE_KEY = "age";
  static final String COMPANY_KEY = "company";
  static final String ROLE_KEY = "role";
  private static final int REASONABLE_AGE_LIMIT = 150;
  private static final String ROLE_REGEX = "^(admin|editor|viewer)$";
  public static final String EMAIL_REGEX = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";

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
    if (todo == null) throw new NotFoundResponse("The requested todo was not found");

    ctx.json(todo);
    ctx.status(HttpStatus.OK);
  }

  public void getTodos(Context ctx) {
    Bson filter = constructFilter(ctx);
    Bson sort = Sorts.ascending("name");

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

    if (ctx.queryParamMap().containsKey(AGE_KEY)) {
      int age = ctx.queryParamAsClass(AGE_KEY, Integer.class)
        .check(a -> a > 0, "Todo's age must be > 0")
        .check(a -> a < REASONABLE_AGE_LIMIT, "Todo's age must be < " + REASONABLE_AGE_LIMIT)
        .get();
      filters.add(eq(AGE_KEY, age));
    }

    if (ctx.queryParamMap().containsKey(COMPANY_KEY)) {
      String company = ctx.queryParam(COMPANY_KEY);
      filters.add(regex(COMPANY_KEY, Pattern.compile(Pattern.quote(company), Pattern.CASE_INSENSITIVE)));
    }

    if (ctx.queryParamMap().containsKey(ROLE_KEY)) {
      String role = ctx.queryParamAsClass(ROLE_KEY, String.class)
        .check(r -> r.matches(ROLE_REGEX), "Invalid todo role")
        .get();
      filters.add(eq(ROLE_KEY, role));
    }

    if (ctx.queryParamMap().containsKey("status")) {
      String status = ctx.queryParam("status");
      if (status != null) filters.add(eq("completed", status.equalsIgnoreCase("complete")));
    }

    if (ctx.queryParamMap().containsKey("contains")) {
      String contains = ctx.queryParam("contains");
      if (contains != null && !contains.isEmpty()) {
        filters.add(regex("body", Pattern.compile(Pattern.quote(contains), Pattern.CASE_INSENSITIVE)));
      }
    }

    return filters.isEmpty() ? new Document() : and(filters);
  }

  public void addNewTodo(Context ctx) {
    String body = ctx.body();
    Todo todo = ctx.bodyValidator(Todo.class)
      .check(t -> t.name != null && !t.name.isEmpty(), "Non-empty name required")
      .check(t -> t.email != null && t.email.matches(EMAIL_REGEX), "Valid email required")
      .check(t -> t.age > 0 && t.age < REASONABLE_AGE_LIMIT, "Valid age required")
      .check(t -> t.role != null && t.role.matches(ROLE_REGEX), "Valid role required")
      .check(t -> t.company != null && !t.company.isEmpty(), "Non-empty company required")
      .get();

    todo.avatar = generateAvatar(todo.email);
    todoCollection.insertOne(todo);

    ctx.json(Map.of("id", todo._id));
    ctx.status(HttpStatus.CREATED);
  }

  public void deleteTodo(Context ctx) {
    ObjectId id;
    try {
      id = new ObjectId(ctx.pathParam("id"));
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("ID wasn't a legal Mongo Object ID");
    }

    DeleteResult result = todoCollection.deleteOne(eq("_id", id));
    if (result.getDeletedCount() == 0) throw new NotFoundResponse("Todo not found");

    ctx.status(HttpStatus.NO_CONTENT);
  }

  String generateAvatar(String email) {
    try {
      return "https://gravatar.com/avatar/" + md5(email) + "?d=identicon";
    } catch (NoSuchAlgorithmException | NullPointerException e) {
      return "https://gravatar.com/avatar/?d=mp";
    }
  }

  public String md5(String str) throws NoSuchAlgorithmException {
    if (str == null) throw new NullPointerException();
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] hash = md.digest(str.toLowerCase().getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    for (byte b : hash) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  @Override
  public void addRoutes(Javalin server) {
    server.get(API_TODO_BY_ID, this::getTodo);
    server.get(API_TODOS, this::getTodos);
    server.post(API_TODOS, this::addNewTodo);
    server.delete(API_TODO_BY_ID, this::deleteTodo);
  }

  JacksonMongoCollection<Todo> getTodoCollection() {
    return todoCollection;
  }
}
