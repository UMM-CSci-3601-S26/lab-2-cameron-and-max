package umm3601.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import org.mockito.MockitoAnnotations;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;

import io.javalin.validation.Validation;
import io.javalin.validation.Validator;

@SuppressWarnings({ "MagicNumber" })
public class TodoControllerSpec {

  private static MongoClient mongoClient;
  private static MongoDatabase db;

  @Mock

  private Context ctx;

  private TodoController todoController;

  @Captor
  private ArgumentCaptor<List<Todo>> todoArrayListCaptor;

  @Captor
  private ArgumentCaptor<Todo> todoCaptor;

  private ObjectId todoId;

  @BeforeAll
  static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
        MongoClientSettings.builder()
            .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
            .build());
    db = mongoClient.getDatabase("test-todos");
  }

  @AfterAll
  static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  void setupEach() {

    MockitoAnnotations.openMocks(this);

    MongoCollection<Document> todoDocuments = db.getCollection("todos");
    todoDocuments.drop();

    List<Document> testTodos = new ArrayList<>();
    testTodos.add(new Document()
        .append("owner", "Test Todo 1")
        .append("body", "Finish lab 2.")
        .append("category", "homework")
        .append("status", false));
    testTodos.add(new Document()
        .append("owner", "Test Todo 2")
        .append("body", "finish level 2.")
        .append("category", "video games")
        .append("status", false)

    );

    todoId = new ObjectId();
    Document specialTodo = new Document()
        .append("owner", "Test Todo 3")
        .append("body", "Lab Meeting at 3PM.")
        .append("category", "software design")
        .append("status", true);

    todoDocuments.insertMany(testTodos);
    todoDocuments.insertOne(specialTodo);

    todoController = new TodoController(db);
  }

  // Test for todo 1 - return all todos
  @Test
    void canGetAllTodos() throws IOException {
        // Mocking queryParamMap to simulate the absence of any query parameters
        when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
        //call controller
        todoController.getTodos(ctx);
        // capture the response and verify HTTP
        verify(ctx).json(todoArrayListCaptor.capture()); //TodoArrayListCaptor needs definition
        verify(ctx).status(HttpStatus.OK);
        List<Todo> returnedTodos = todoArrayListCaptor.getValue();
        //get returned list
        assertEquals(
         db.getCollection("todos").countDocuments(),
          returnedTodos.size()
        );



    }
    

  // Test for todo 2 - Return Todo with an ID
  @Test
  void getTodoWithExistentId() throws IOException {
    Todo todo = new Todo();

    todo._id = new ObjectId().toHexString();
    todo.owner = "Test Owner";
    todo.status = false;
    todo.body = "Test body";
    todo.category = "software design";

    todoController.getTodoCollection().insert(todo);

    when(ctx.pathParam("id")).thenReturn(todo._id);

    todoController.getTodo(ctx);

    verify(ctx).json(todoCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    assertEquals(todo._id, todoCaptor.getValue()._id);
  }

  // Todo 3 - Limit Number of Todos displayed
  @Test
  void canGetLimitedByNumberOfTodos() throws IOException {
    // Mock the "limit" query parameter to return 2

    when(ctx.queryParam("limit")).thenReturn("2");

    // call controller
    todoController.getTodos(ctx);

    // capture the response and verify HTTP
    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    List<Todo> todosReturned = todoArrayListCaptor.getValue();

    // 2 todos are returned
    assertEquals(2, todosReturned.size());

    assertEquals("Test Todo 3", todosReturned.get(0)._id);
    assertEquals("Test Todo 1", todosReturned.get(1)._id);

  }

  // Todo 4 - Filter Todo by status
  @Test
  void canFilterByStatus() throws IOException {
    // Mock the "status" query parameter as "complete"
    when(ctx.queryParam("status")).thenReturn("complete");

    // call controller
    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    List<Todo> todosReturned = todoArrayListCaptor.getValue();

    assertTrue(todosReturned.stream().anyMatch(todo -> todo.isCompleted()));

  }

  // Todo 5 - Search Todos By String
  @Test

  void canSearchTodosByString() throws IOException {

    
    // Mock the "contains" query parameter as "todo"
    when(ctx.queryParam("contains")).thenReturn("todo");

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    List<Todo> todosReturned = todoArrayListCaptor.getValue();

    assertTrue(todosReturned.stream().allMatch(todo -> todo._id.toLowerCase().contains("todo")));
  }

  @Test
  void getTodoWithInvalidId() throws IOException {
    when(ctx.pathParam("id")).thenReturn("invalidId");

    try {
      todoController.getTodo(ctx);
    } catch (BadRequestResponse e) {
      assertTrue(e.getMessage().contains("wasn't a legal Mongo Object ID"));
    }

  }

  @Test
  void getTodoWithNonExistentId() throws IOException {
    String fakeId = new ObjectId().toHexString();
    when(ctx.pathParam("id")).thenReturn(fakeId);

    try {
      todoController.getTodo(ctx);
    } catch (NotFoundResponse e) {
      assertTrue(e.getMessage().contains("was not found"));
    }
  }

  @Test
  void getTodosWithNoQueryParams() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    when(ctx.queryParam("limit")).thenReturn(null);
    when(ctx.queryParam("status")).thenReturn(null);
    when(ctx.queryParam("contains")).thenReturn(null);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    List<Todo> todosReturned = todoArrayListCaptor.getValue();
    assertEquals((int) db.getCollection("todos").countDocuments(), todosReturned.size());

  }

  @Test
  void getTodosFromEmptyDatabase() throws IOException {
    db.getCollection("todos").drop();
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    assertTrue(todoArrayListCaptor.getValue().isEmpty());
  }

}
