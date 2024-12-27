package com.aestallon.storageexplorer.swing.ui.dialog.importstorage;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.service.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;

public final class ImportStorageController extends AbstractDialogController<StorageInstanceDto> {

  public static ImportStorageController forCreatingNew(
      final StorageInstanceProvider storageInstanceProvider) {
    return new ImportStorageController(
        new StorageInstanceDto(),
        (before, after) -> CompletableFuture.runAsync(
            () -> storageInstanceProvider.importAndIndex(StorageInstance.fromDto(after))));
  }

  public static ImportStorageController forUpdating(final StorageInstance storageInstance,
                                                    final UserConfigService userConfigService,
                                                    final StorageInstanceProvider storageInstanceProvider,
                                                    final Consumer<StorageInstanceDto> f) {
    return new ImportStorageController(
        storageInstance.toDto(),
        (before, after) -> CompletableFuture.runAsync(() -> {
          userConfigService.updateStorageLocation(after);
          storageInstance.applyDto(after);
          
          if (!Objects.equals(before.getType(), after.getType()) ||
              !Objects.equals(before.getFs(), after.getFs()) ||
              !Objects.equals(before.getDb(), after.getDb())) {
            // if the User changed the Storage location, we must drop the current context and redo
            // almost everything:
            storageInstanceProvider.reimport(storageInstance);
          } else if (!Objects.equals(before.getIndexingStrategy(), after.getIndexingStrategy())) {
              // if only the indexing strategy is changed, we may just re-index:
            storageInstanceProvider.reindex(storageInstance);
          }
        }),
        f);
  }

  public ImportStorageController(final StorageInstanceDto initialModel,
                                 final Finisher<StorageInstanceDto> finisher) {
    super(initialModel, finisher);
  }

  public ImportStorageController(final StorageInstanceDto initialModel,
                                 final Finisher<StorageInstanceDto> finisher,
                                 final Consumer<StorageInstanceDto> postProcessor) {
    super(initialModel, finisher, postProcessor);
  }
}
