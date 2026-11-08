package com.aestallon.storageexplorer.swing.ui.dialog.loadentry;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.core.event.EntryAcquired;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.util.Uris;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;
import com.aestallon.storageexplorer.swing.ui.misc.StorageEntryResolver;

public class LoadEntryController extends AbstractDialogController<LoadEntryDialogModel> {

  public static LoadEntryController create(final ApplicationEventPublisher eventPublisher,
                                           final StorageInstance storageInstance,
                                           final StorageInstanceProvider storageInstanceProvider,
                                           final UserConfigService userConfigService) {
    return new LoadEntryController(
        new LoadEntryDialogModel(
            "",
            storageInstance,
            storageInstanceProvider.provide().toList()),
        (before, after) ->  {
          CompletableFuture.runAsync(() -> StorageEntryResolver
              .resolve(after.selection(), URI.create(after.input()))
              .ifPresentOrElse(
                  it -> eventPublisher.publishEvent(new EntryAcquired(after.selection(), it)),
                  () -> SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                      null,
                      "Cannot show URI: " + after.input(),
                      "Unreachable URI",
                      JOptionPane.ERROR_MESSAGE))));
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
