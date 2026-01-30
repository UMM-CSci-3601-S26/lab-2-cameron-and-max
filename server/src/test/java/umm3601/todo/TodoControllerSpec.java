package umm3601.todo;

import umm3601.todo.Todo;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.mongodb.client.model.Filters.eq;



import static org.junit.jupiter.api.Assertions.fail;


import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.jetty.util.IO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mongojack.JacksonMongoCollection;
import org.mongojack.internal.util.FindIterableDecorator;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.validation.BodyValidator;
import io.javalin.validation.Validation;
import io.javalin.validation.Validator;
import umm3601.user.UserController;




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
            .applyToClusterSettings(builder ->
                builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
            .build()
    );
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
        .append("name", "Test Todo 1")
        .append("age", 25)
        .append("company", "ACME")
        .append("email", "one@test.com")
        .append("role", "admin")
        .append("avatar", "https://gravatar.com/avatar/example1?d=identicon")
        .append("completed", false)
    );
    testTodos.add(new Document()
        .append("name", "Test Todo 2")
        .append("age", 30)
        .append("company", "Globex")
        .append("email", "two@test.com")
        .append("role", "editor")
        .append("avatar", "https://gravatar.com/avatar/example2?d=identicon")
        .append("completed", true)
      );


    todoId = new ObjectId();
    Document specialTodo = new Document()
        .append("_id", todoId)
        .append("name", "Special Todo")
        .append("age", 35)
        .append("company", "Initech")
        .append("email", "special@test.com")
        .append("role", "viewer")
        .append("avatar", "https://gravatar.com/avatar/example3?d=identicon")
        .append("completed", true);

    todoDocuments.insertMany(testTodos);
    todoDocuments.insertOne(specialTodo);

    todoController = new TodoController(db);
}




//Test for todo 1 - return all todos
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

    @Test
  void canGetTodosWithAge35() throws IOException {

    Integer targetAge = 35;
    String targetAgeString = targetAge.toString();


    Map<String, List<String>> queryParams = new HashMap<>();

    queryParams.put(TodoController.AGE_KEY, Arrays.asList(new String[] {targetAgeString}));

    when(ctx.queryParamMap()).thenReturn(queryParams);

    when(ctx.queryParam(TodoController.AGE_KEY)).thenReturn(targetAgeString);

    Validation validation = new Validation();

    Validator<Integer> validator = validation.validator(TodoController.AGE_KEY, Integer.class, targetAgeString);

    when(ctx.queryParamAsClass(TodoController.AGE_KEY, Integer.class))
        .thenReturn(validator);

    todoController.getTodos(ctx);


    verify(ctx).json(todoArrayListCaptor.capture());

    verify(ctx).status(HttpStatus.OK);

    // Confirm that we get back two users.
    assertEquals(1, todoArrayListCaptor.getValue().size());
    // Confirm that both users have age 37.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetAge, todo.age);
    }

    List<String> names = todoArrayListCaptor.getValue().stream().map(user -> user.name).collect(Collectors.toList());

    assertTrue(names.contains("Special Todo"));

  }

//Test for todo 2 - Return Todo with an ID
    @Test
    void getTodoWithExistentId() throws IOException {
        String idStr = todoId.toHexString();
        when(ctx.pathParam("id")).thenReturn(idStr);

        todoController.getTodo(ctx);

        verify(ctx).json(todoCaptor.capture());
        verify(ctx).status(HttpStatus.OK);
        assertEquals(idStr, todoCaptor.getValue()._id);
    }
//Todo 3 - Limit Number of Todos displayed
    @Test
    void canGetLimitedByNumberOfTodos() throws IOException {
    //Mock the "limit" query parameter to return 2

      when(ctx.queryParam("limit")).thenReturn("2");


      // call controller
      todoController.getTodos(ctx);

      // capture the response and verify HTTP
      verify(ctx).json(todoArrayListCaptor.capture());
      verify(ctx).status(HttpStatus.OK);

      List<Todo> todosReturned = todoArrayListCaptor.getValue();

      //2 todos are returned
      assertEquals(2, todosReturned.size());

      assertEquals("Special Todo", todosReturned.get(0).name);
      assertEquals("Test Todo 1", todosReturned.get(1).name);




    }


//Todo 4 - Filter Todo by status
    @Test
    void canFilterByStatus() throws IOException {
      // Mock the "status" query parameter as "complete"
      when(ctx.queryParam("status")).thenReturn("complete");


      //call controller
      todoController.getTodos(ctx);

      verify(ctx).json(todoArrayListCaptor.capture());
      verify(ctx).status(HttpStatus.OK);

      List<Todo> todosReturned = todoArrayListCaptor.getValue();

      assertTrue(todosReturned.stream().anyMatch(todo -> todo.isCompleted()));


    }

//Todo 5 - Search Todos By String
    @Test

    void canSearchTodosByString() throws IOException {
     // Mock the "contains" query parameter as "todo"
      when(ctx.queryParam("contains")).thenReturn("todo");

      todoController.getTodos(ctx);

      verify(ctx).json(todoArrayListCaptor.capture());
      verify(ctx).status(HttpStatus.OK);

      List<Todo> todosReturned = todoArrayListCaptor.getValue();

      assertTrue(todosReturned.stream().allMatch(todo -> todo.name.toLowerCase().contains("todo")));
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
  void deleteTodoWithInvalidId() throws IOException {
    when(ctx.pathParam("id")).thenReturn("invalidId");

    BadRequestResponse e = assertThrows(
        BadRequestResponse.class,
        () -> todoController.deleteTodo(ctx)
    );


    assertTrue(e.getMessage().contains("ID wasn't a legal Mongo Object ID"));
}




    @Test
    void addNewTodoWithInvalidEmail() throws IOException {
      String badTodoJson = """
        {
          "name" : "Test",
          "email": "not-an-email",
          "age": 25,
          "role": "admin",
          "company": "ACME"
        }
      """;
      when(ctx.body()).thenReturn(badTodoJson);
      var fakeValidator = mock(io.javalin.validation.BodyValidator.class);
      when(ctx.bodyValidator(Todo.class)).thenReturn(fakeValidator);
      when(fakeValidator.check(any(), anyString())).thenReturn(fakeValidator);
      when(fakeValidator.get()).thenThrow(new BadRequestResponse("Todo must have a legal email"));


      BadRequestResponse e = assertThrows(
          BadRequestResponse.class,
          () -> todoController.addNewTodo(ctx)
      );
        assertTrue(e.getMessage().contains("legal email"));
    }


    @Test
    void addNewTodoWithInvalidAge() throws IOException {
      String badTodoJson = """
        {
          "name" : "Test",
          "email": "test@test.com",
          "age": 0,
          "role": "admin",
          "company": "ACME"
        }
      """;
      when(ctx.body()).thenReturn(badTodoJson);

      var fakeValidator = mock(io.javalin.validation.BodyValidator.class);
      when(ctx.bodyValidator(Todo.class)).thenReturn(fakeValidator);
      when(fakeValidator.check(any(), anyString())).thenReturn(fakeValidator);
      when(fakeValidator.get()).thenThrow(new BadRequestResponse("Todo age must be greater than 0"));


      BadRequestResponse e = assertThrows(
          BadRequestResponse.class,
          () -> todoController.addNewTodo(ctx)
      );
        assertTrue(e.getMessage().contains("greater than 0"));

    }

    @Test
    void addNewTodoWithEmptyName() throws IOException {
      String badTodoJson = """
        {
          "name" : "",
          "email": "test@test.com",
          "age": 25,
          "role": "admin",
          "company": "ACME"
        }
      """;


      when(ctx.body()).thenReturn(badTodoJson);
      var fakeValidator = mock(io.javalin.validation.BodyValidator.class);
      when(ctx.bodyValidator(Todo.class)).thenReturn(fakeValidator);
      when(fakeValidator.check(any(), anyString())).thenReturn(fakeValidator);
      when(fakeValidator.get()).thenThrow(new BadRequestResponse("non-empty todo name"));


      BadRequestResponse e = assertThrows(
          BadRequestResponse.class,
          () -> todoController.addNewTodo(ctx)
      );
        assertTrue(e.getMessage().contains("non-empty todo name"));

    }

    @Test
    void addNewTodoWithInvalidRole() throws IOException {
      String badTodoJson = """
        {
          "name" : "Test",
          "email": "test@test.com",
          "age": 25,
          "role": "InvalidRole",
          "company": "ACME"
        }
      """;
      when(ctx.body()).thenReturn(badTodoJson);
      var fakeValidator = mock(io.javalin.validation.BodyValidator.class);
      when(ctx.bodyValidator(Todo.class)).thenReturn(fakeValidator);
      when(fakeValidator.check(any(), anyString())).thenReturn(fakeValidator);
      when(fakeValidator.get()).thenThrow(new BadRequestResponse("legal todo role"));


      BadRequestResponse e = assertThrows(
          BadRequestResponse.class,
          () -> todoController.addNewTodo(ctx)
      );
        assertTrue(e.getMessage().contains("legal todo role"));

    }

    @Test
    void addNewTodoWithEmptyCompany() throws IOException {
      String badTodoJson = """
        {
          "name" : "Test",
          "email": "test@test.com",
          "age": 25,
          "role": "admin",
          "company": "ACME"
        }
      """;
      when(ctx.body()).thenReturn(badTodoJson);
      var fakeValidator = mock(io.javalin.validation.BodyValidator.class);
      when(ctx.bodyValidator(Todo.class)).thenReturn(fakeValidator);
      when(fakeValidator.check(any(), anyString())).thenReturn(fakeValidator);
      when(fakeValidator.get()).thenThrow(new BadRequestResponse("non-empty company name"));


      BadRequestResponse e = assertThrows(
          BadRequestResponse.class,
          () -> todoController.addNewTodo(ctx)
      );
        assertTrue(e.getMessage().contains("non-empty company name"));

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
    void testGenerateAvatar() throws NoSuchAlgorithmException {
    // Arrange
      String email = "test@example.com";
      TodoController controller = Mockito.spy(todoController);
      when(controller.md5(email)).thenReturn("md5hash");

    // Act
      String avatar = controller.generateAvatar(email);

    // Assert
     assertEquals("https://gravatar.com/avatar/md5hash?d=identicon", avatar);
    }

    @Test
  void testGenerateAvatarWithException() throws NoSuchAlgorithmException {

    String email = "test@example.com";
    TodoController controller = Mockito.spy(todoController);
    when(controller.md5(email)).thenThrow(NoSuchAlgorithmException.class);


    String avatar = controller.generateAvatar(email);


    assertEquals("https://gravatar.com/avatar/?d=mp", avatar);
  }

   @Test
  void getTodosByCompanyAndAge() throws IOException {
    String targetCompanyString = "OHMNET";
    Integer targetAge = 37;
    String targetAgeString = targetAge.toString();

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.COMPANY_KEY, Arrays.asList(new String[] {targetCompanyString}));
    queryParams.put(TodoController.AGE_KEY, Arrays.asList(new String[] {targetAgeString}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.COMPANY_KEY)).thenReturn(targetCompanyString);


    Validation validation = new Validation();
    Validator<Integer> validator = validation.validator(TodoController.AGE_KEY, Integer.class, targetAgeString);
    when(ctx.queryParamAsClass(TodoController.AGE_KEY, Integer.class)).thenReturn(validator);
    when(ctx.queryParam(TodoController.AGE_KEY)).thenReturn(targetAgeString);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals(0, todoArrayListCaptor.getValue().size());
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetCompanyString, todo.company);
      assertEquals(targetAge, todo.age);
    }
  }

  @Test
  void getTodosFromEmptyDatabase() throws IOException {
    db.getCollection("todos").drop();
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    assertTrue(todoArrayListCaptor.getValue().isEmpty());
  }

  @Test
  void deleteExistingTodo()  throws IOException {
    String idStr = todoId.toHexString();
    when(ctx.pathParam("id")).thenReturn(idStr);

    todoController.deleteTodo(ctx);

    verify(ctx).status(HttpStatus.NO_CONTENT);
    assertEquals(0, db.getCollection("todos").countDocuments(eq("_id", todoId)));
  }































  }




