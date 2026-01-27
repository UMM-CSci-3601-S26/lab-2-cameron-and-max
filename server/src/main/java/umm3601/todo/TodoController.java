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
import java.util.Objects;
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
  static final String SORT_ORDER_KEY = "sortorder";
  private static final int REASONABLE_AGE_LIMIT = 150;
  private static final String ROLE_REGEX = "^(admin|editor|viewer)$";
  public static final String EMAIL_REGEX = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";

  private final JacksonMongoCollection<Todo> todoCollection;

  public TodoController(MongoDatabase database) {
    todoCollection = JacksonMongoCollection.builder().build(
      database,
      "todos", Todo.class, UuidRepresentation.STANDARD);
  }

  public void getTodo(Context ctx) {
    String id = ctx.pathParam("id");
    Todo todo;

     try {
      todo = todoCollection.find(eq("id", new ObjectId(id))).first();
     } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested todo id wasn't a legal Mongo Object ID.");
     }
     if (todo == null) {
      throw new NotFoundResponse("The requested todo was not found");
     } else {
      ctx.json(todo);
      ctx.status(HttpStatus.OK);
     }
  }

  public void getTodos(Context ctx) {
    Bson combinedFilter = constructFilter(ctx);
    Bson sortingOrder = constructSortingOrder(ctx);
    ArrayList<Todo> matchingUSers = todoCollection
      .find(combinedFilter)
      .sort(sortingOrder)
      .into(new ArrayList<>());
    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);

  }

  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>();

    if (ctx.queryParamMap().containsKey(AGE_KEY)) {
      int targetAge = ctx.queryParamAsClass(AGE_KEY, Integer.class)
        .check(it -> it > 0, "Todo's age must be greater than zero; you provided " + ctx.queryParam(AGE_KEY))
        .check(it -> it < REASONABLE_AGE_LIMIT,
          "Todo's age muust be less than " + REASONABLE_AGE_LIMIT + "; you provided " + ctx.queryParam(AGE_KEY))
        .get();
        filters.add(eq(AGE_KEY, targetAge));
    }
    if (ctx.queryParamMap().containsKey(COMPANY_KEY)) {
      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(COMPANY_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex(COMPANY_KEY, pattern));

    }
    if (ctx.queryParamMap().containsKey(ROLE_KEY)) {
      String role = ctx.queryParamAsClass(ROLE_KEY, String.class)
        .check(it -> it.matches(ROLE_REGEX), "Todo must have legal todo role")
        .get();
      filters.add(eq(ROLE_KEY, role));
    }
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;

  }


  private Bson constructSortingOrder(Context ctx) {
    String sortBy = Objects.requireNonNull(ctx.queryParam("sortBy"), "name");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ? Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }

  public void getTodosGroupedByCompany(Context ctx) {
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortBy"), "_id");
    if (sortBy.equals("company")) {
      sortBy = "_id";
    }
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortOrder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ? Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    ArrayList<TodoByCompany. matchingTodos = todoCollection
      .aggregate(
        List.of(
          new Document("$project", new Document("_id", 1).append("name", 1).append("comapny", 1)),
          new document("$group", new Document("_id", "$company")
            .append("count", new Document("$sum", 1))
            .append("todos". new Document("$push", new Document("_id", "$_id").append("name", "$name")))),
          new Document("$sort", sortingOrder)
        ),
        TodoByCompany.class
      )
      .into(new ArrayList<>());
      ctx.json(matchingTodos);
      ctx.status(HttpStatus.OK);

  }

  public void addNewTodo(Context ctx) {
    String body =ctx.body();
    Todo newtTodo = ctx.bodyValidator(Todo.class)
      .check(tod -> tod.name != null && tod.name.length() > 0,
        "Todo must have a non-empty todo name; body was" + body)
      .check(tod -> tod.email.matches(EMAIL_REGEX),
        "Todo must have a legal email; body was " + body)
      .check(tod -> tod.age > 0,
        "Todo age must be greater than 0: body was " + body)
      .check(tod -> tod.age < REASONABLE_AGE_LIMIT,
        "Todo's age must be less than " + REASONABLE_AGE_LIMIT + "; body was " + body)
      .check(tod-> tod.role.matches(ROLE_REGEX),
        "Todo must have a legal todo role; body was " + body)
      .check(tod -> tod.company != null && tod.company.length() > 0,
        "Todo must have a non-empty company name; body was " + body)
      .get();
    newTodo.avatar = generateAvatar(newTodo.email);
    todoCollection.insertOne(newTodo);
    ctx.json(Map.of("id", newTodo._id));
    ctx.status(HttpStatus.CREATED);

  }

  public void deleteTodo(Context ctx) {
    String id = ctx.pathParam("id");
    DeleteResult deleteResult = todoCollection.deleteOne(eq("_id", new ObjectId(id)));
    if (deleteResult.getDeletedCount() != 1) {
      ctx.status(HttpStatus.NOT_FOUND);
      throw new NotFoundResponse(
        "Was unable to delete ID "
          + id
          + "; perhaps illegal ID or an ID for an item not in the system?");
      }
      ctx.status(HttpStatus.OK);

  }

  String generateAvatar(String email) {
    String avatar;
    try {
      avatar = "https://gravatar.com/avatar/" + md5(email) + "?d=identicon";
    } catch (NoSuchAlgorithmException ignored) {
      avatar = "https://gravatar.com/avatar/?d=mp";
    }
    return avatar;
  }

  public String md5 (String str) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] hashInBytes = md.digest(str.toLowerCase().getBytes(StandardCharsets.UTF_8));
    StringBuilder result = new StringBuilder();
    for (byte b : hashInBytes) {
      result.append(String.format("%02x", b));

    }
    return result.toString();
  }

  @Override
  public void addRoutes(Javalin server) {
    server.get(API_TODO_BY_ID, this::getTodo);
    server.get(API_TODOS, this::getTodos);
    server.get("/api/todosByCompany", this::getTodosGroupedByCompany);
    server.post(API_TODOS, this::addNewTodo);
    server.delete(API_TODO_BY_ID, this::deleteTodo);
  }
}
