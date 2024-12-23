package hu.aestallon.storageexplorer.ui.dialog.loadentry;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import org.smartbit4all.core.utility.StringConstant;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.domain.storage.model.instance.StorageInstance;
import hu.aestallon.storageexplorer.ui.controller.AbstractDialogController;

public class LoadEntryController extends AbstractDialogController<String> {

  public static LoadEntryController create(StorageInstance storageInstance) {
    return new LoadEntryController((before, after) ->
        CompletableFuture.runAsync(() -> storageInstance.acquire(URI.create(after))));
  }

  public static LoadEntryDialog newDialog(StorageInstance storageInstance) {
    final var controller = create(storageInstance);
    final var dialog = new LoadEntryDialog(controller);
    dialog.setTitle("Load Entry From " + storageInstance.name());
    return dialog;
  }

  public LoadEntryController(Finisher<String> finisher) {
    super(StringConstant.EMPTY, finisher);
  }

  boolean validate(final String input) {
    // TODO: Do a more robust checking for Storage compliant URI shapes...
    if (Strings.isNullOrEmpty(input)) {
      return false;
    }

    if (input.length() < 4) {
      return false;
    }

    if (!input.contains(":/")) {
      return false;
    }

    try {
      new URI(input);
      return true;
    } catch (final URISyntaxException e) {
      return false;
    }

  }

}
