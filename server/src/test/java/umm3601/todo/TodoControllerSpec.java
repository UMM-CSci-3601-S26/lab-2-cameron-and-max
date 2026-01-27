package umm3601.todo;

public class TodoControllerSpec {


//Test for todo 1 - return all todos
    @Test
    void canGetAllTodos() throws IOException {
        when(ctx.quearyParamMap()).thenReturn(Collections.emptyMap());
        TodoController.getTodos(ctx);

        verify(ctx).json(TodoArrayListCaptor.capture) //TodoArrayListCaptor needs definition
        verify(ctx).status(HttpStatus.OK);

        assertEquals(
        db.getCollection("todos").countDocuments(),
        TodoArrayListCaptor.getValue().size());
    }
//Test for todo 2 - Return Todo with an ID
    @Test
    void getTodoWithExistentId() throws IOException {
        String id = id.toHexString();
        when(ctx.pathParam("id")).thenReturn(id);

        TodoController.getTodo(ctx);

        verify(ctx).json(TodoCaptor.capture());
        verify(ctx).status(HttpStatus.OK);
        assertEquals(id.toHexString(), Todocaptor.getvalue(._id))
    }
//Todo 3 - Limit Number of Todos displayed
    @Test
    void countTodos


//Todo 4 -
    @Test
    void canFilterbyStatus(){

    }

//Todo 5 -
    @Test






}
