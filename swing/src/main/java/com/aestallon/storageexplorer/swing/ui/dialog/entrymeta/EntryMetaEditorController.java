package com.aestallon.storageexplorer.swing.ui.dialog.entrymeta;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;

public class EntryMetaEditorController
    extends AbstractDialogController<StorageEntryTrackingService.StorageEntryUserData> {

  public static EntryMetaEditorController newInstance(final StorageEntry storageEntry,
                                                      final StorageEntryTrackingService trackingService) {
    return new EntryMetaEditorController(
        trackingService
            .getUserData(storageEntry)
            .orElseGet(() -> new StorageEntryTrackingService.StorageEntryUserData("", "")),
        (before, after) -> {
          if (after.equals(before)) {
            return;
          }

          CompletableFuture.runAsync(() -> trackingService.updateStorageEntryUserData(
              storageEntry,
              after));
        });
  }

  public static EntryMetaEditorController dummy() {
    return new EntryMetaEditorController(
        new StorageEntryTrackingService.StorageEntryUserData("", ""),
        (before, after) -> System.out.println(after));
  }


  protected EntryMetaEditorController(
      StorageEntryTrackingService.StorageEntryUserData initialModel,
      Finisher<StorageEntryTrackingService.StorageEntryUserData> finisher) {
    super(initialModel, finisher);
  }

  protected EntryMetaEditorController(
      StorageEntryTrackingService.StorageEntryUserData initialModel,
      Finisher<StorageEntryTrackingService.StorageEntryUserData> finisher,
      Consumer<StorageEntryTrackingService.StorageEntryUserData> postProcessor) {
    super(initialModel, finisher, postProcessor);
  }
}
