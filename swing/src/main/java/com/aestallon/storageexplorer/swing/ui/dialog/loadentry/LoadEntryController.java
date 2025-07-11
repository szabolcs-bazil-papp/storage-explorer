package com.aestallon.storageexplorer.swing.ui.dialog.loadentry;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;

public class LoadEntryController extends AbstractDialogController<LoadEntryDialogModel> {

  private static final Set<String> STORED_COLLECTION_IDENTIFIERS = Set.of(
      StorageEntryFactory.STORED_LIST_MARKER,
      StorageEntryFactory.STORED_MAP_MARKER,
      StorageEntryFactory.STORED_REF_MARKER,
      StorageEntryFactory.STORED_SEQ_MARKER);
  private static final String REGEX_TIMESTAMP = "/\\d{4}/\\d{1,2}/\\d{1,2}/\\d{1,2}";
  private static final Pattern PATTERN_TIMESTAMP = Pattern.compile(REGEX_TIMESTAMP);

  public static LoadEntryController create(final StorageInstance storageInstance,
                                           final StorageInstanceProvider storageInstanceProvider,
                                           final UserConfigService userConfigService) {
    return new LoadEntryController(
        new LoadEntryDialogModel(
            "", 
            storageInstance, 
            storageInstanceProvider.provide().toList()),
        (before, after) ->  {
          CompletableFuture.runAsync(() -> after.selection().acquire(URI.create(after.input())));
          CompletableFuture.runAsync(() -> userConfigService
              .setMostRecentStorageInstanceLoad(after.selection().id()));
        });
  }

  protected LoadEntryController(LoadEntryDialogModel initialModel,
                                Finisher<LoadEntryDialogModel> finisher) {
    super(initialModel, finisher);
  }

  protected LoadEntryController(LoadEntryDialogModel initialModel,
                                Finisher<LoadEntryDialogModel> finisher,
                                Consumer<LoadEntryDialogModel> postProcessor) {
    super(initialModel, finisher, postProcessor);
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
    
    if (input.endsWith(".") || input.endsWith(".v")) {
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
