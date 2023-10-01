package example.myapp.shelf;

import example.myapp.shelf.grpc.GetShelfRequest;
import example.myapp.shelf.grpc.Shelf;
import example.myapp.shelf.grpc.ShelfService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ShelfServiceImpl implements ShelfService {
  public ShelfServiceImpl() {
  }

  @Override
  public CompletionStage<Shelf> getShelf(GetShelfRequest in) {
    Shelf shelf = Shelf.newBuilder().setId(1).setTheme("halloween").build();
    return CompletableFuture.completedFuture(shelf);
  }
}
