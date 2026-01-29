package umm3601.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import umm3601.user.UserController;




public class TodoControllerSpec {

  private static MongoClient mongoClient;
  private static MongoDatabase db;


@Mock

private Context ctx;


private TodoController todoController;

@Captor
private ArgumentCaptor<ArrayList<Todo>> TodoArrayListCaptor;

@Captor
private ArgumentCaptor<Todo> TodoCaptor;

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
        .append("avatar", "https://gravatar.com/avatar/example1?d=identicon"));
    testTodos.add(new Document()
        .append("name", "Test Todo 2")
        .append("age", 30)
        .append("company", "Globex")
        .append("email", "two@test.com")
        .append("role", "editor")
        .append("avatar", "https://gravatar.com/avatar/example2?d=identicon"));


    todoId = new ObjectId();
    Document specialTodo = new Document()
        .append("_id", todoId)
        .append("name", "Special Todo")
        .append("age", 35)
        .append("company", "Initech")
        .append("email", "special@test.com")
        .append("role", "viewer")
        .append("avatar", "https://gravatar.com/avatar/example3?d=identicon");

    todoDocuments.insertMany(testTodos);
    todoDocuments.insertOne(specialTodo);

    todoController = new TodoController(db);
}




//Test for todo 1 - return all todos
    @Test
    void canGetAllTodos() throws IOException {
        when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
        todoController.getTodos(ctx);

        verify(ctx).json(TodoArrayListCaptor.capture()); //TodoArrayListCaptor needs definition
        verify(ctx).status(HttpStatus.OK);

        assertEquals(
         db.getCollection("todos").countDocuments(),
          TodoArrayListCaptor.getValue().size()
        );
    }
//Test for todo 2 - Return Todo with an ID
    @Test
    void getTodoWithExistentId() throws IOException {
        String idStr = todoId.toHexString();
        when(ctx.pathParam("id")).thenReturn(idStr);

        todoController.getTodo(ctx);

        verify(ctx).json(TodoCaptor.capture());
        verify(ctx).status(HttpStatus.OK);
        assertEquals(idStr, TodoCaptor.getValue()._id);
    }
//Todo 3 - Limit Number of Todos displayed
    @Test
    void canGetLimitedByNumberOfTodos() throws IOException {
      //query parameter
      when(ctx.queryParamMap()).thenReturn(Collections.singletonMap("limit", List.of("2")));
      when(ctx.queryParam("limit")).thenReturn("2");


      // call controller
      todoController.getTodos(ctx);

      //Capture
      verify(ctx).json(TodoArrayListCaptor.capture());
      verify(ctx).status(HttpStatus.OK);

      List<Todo> todosReturned = TodoArrayListCaptor.getValue();

      //2 todos are returned
      assertEquals(2, todosReturned.size());

      assertEquals("Todo 1", todosReturned.get(0).name);
      assertEquals("Todo 2", todosReturned.get(1).name);
    }


//Todo 4 -
    @Test
    void canFilterbyStatus(){

    }

//Todo 5 -
    @Test






}
