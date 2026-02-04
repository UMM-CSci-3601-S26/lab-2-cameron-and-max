package umm3601.todo;


import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({"VisibilityModifier"})
public class Todo {

  @ObjectId @Id

  @SuppressWarnings({"MemberName"})
  public String _id;




  public String owner;
  public String body;
  public String category;
  public Boolean status;

  public boolean isCompleted() {
    return status;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
       return true;
    }
    if (!(obj instanceof Todo)) {
      return false;
    }
    Todo other = (Todo) obj;
    return _id != null && _id.equals(other._id);

  }
  @Override
  public int hashCode() {
    return _id == null ? 0 : _id.hashCode();
  }
  @Override
  public String toString() {
    return _id;
  }
}

