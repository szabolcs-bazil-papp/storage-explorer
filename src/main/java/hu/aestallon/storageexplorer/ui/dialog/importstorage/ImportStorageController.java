package hu.aestallon.storageexplorer.ui.dialog.importstorage;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import hu.aestallon.storageexplorer.domain.storage.model.instance.StorageInstance;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageInstanceDto;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndexProvider;
import hu.aestallon.storageexplorer.domain.userconfig.service.UserConfigService;
import hu.aestallon.storageexplorer.ui.controller.AbstractDialogController;

public final class ImportStorageController extends AbstractDialogController<StorageInstanceDto> {

  public static ImportStorageController forCreatingNew(
      final StorageIndexProvider storageIndexProvider) {
    return new ImportStorageController(
        new StorageInstanceDto(),
        (before, after) -> CompletableFuture.runAsync(
            () -> storageIndexProvider.importAndIndex(StorageInstance.fromDto(after))));
  }

  public static ImportStorageController forUpdating(final StorageInstance storageInstance,
                                                    final UserConfigService userConfigService,
                                                    final StorageIndexProvider storageIndexProvider,
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
            storageIndexProvider.reimport(storageInstance);
          } else if (!Objects.equals(before.getIndexingStrategy(), after.getIndexingStrategy())) {
              // if only the indexing strategy is changed, we may just re-index:
            storageIndexProvider.reindex(storageInstance);
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
