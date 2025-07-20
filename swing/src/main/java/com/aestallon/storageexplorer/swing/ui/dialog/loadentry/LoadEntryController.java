package com.aestallon.storageexplorer.swing.ui.dialog.loadentry;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.util.Uris;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;

public class LoadEntryController extends AbstractDialogController<LoadEntryDialogModel> {



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
    return Uris.parseStr(input).isPresent();
  }



}
