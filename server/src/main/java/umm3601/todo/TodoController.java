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
        .check(it -> it.matches(ROLE_REGEX), "Todo myst have legal todo role")
        .get();
      filters.add(eq(ROLE_KEY, role));
    }
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;

  }



}
