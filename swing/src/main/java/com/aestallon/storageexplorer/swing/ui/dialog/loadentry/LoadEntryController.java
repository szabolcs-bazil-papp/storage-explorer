package com.aestallon.storageexplorer.swing.ui.dialog.loadentry;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;

public class LoadEntryController extends AbstractDialogController<String> {

  private static final Set<String> STORED_COLLECTION_IDENTIFIERS = Set.of(
      StorageEntryFactory.STORED_LIST_MARKER,
      StorageEntryFactory.STORED_MAP_MARKER,
      StorageEntryFactory.STORED_REF_MARKER,
      StorageEntryFactory.STORED_SEQ_MARKER);
  private static final String REGEX_TIMESTAMP = "/\\d{4}/\\d{1,2}/\\d{1,2}/\\d{1,2}";
  private static final Pattern PATTERN_TIMESTAMP = Pattern.compile(REGEX_TIMESTAMP);

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
    super("", finisher);
  }

  boolean validate(final String input) {
    // TODO: Do a more robust checking for Storage compliant URI shapes...
    if (input == null || input.trim().isEmpty()) {
      return false;
    }

    if (input.length() < 4) {
      return false;
    }

    if (!input.contains(":/")) {
      return false;
    }

    // must contain a stored collection identifier or a timestamp segment:
    if (!containsStoredCollectionIdentifier(input) && !containsTimestampPattern(input)) {
      return false;
    }

    try {
      new URI(input);
      return true;
    } catch (final URISyntaxException e) {
      return false;
    }

  }

  private static boolean containsStoredCollectionIdentifier(final String input) {
    return STORED_COLLECTION_IDENTIFIERS.stream().anyMatch(input::contains);
  }
  
  private static boolean containsTimestampPattern(final String input) {
    return PATTERN_TIMESTAMP.matcher(input).find();
  }

}
