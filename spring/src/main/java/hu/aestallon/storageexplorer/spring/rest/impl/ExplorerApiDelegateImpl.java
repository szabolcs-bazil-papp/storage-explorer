package hu.aestallon.storageexplorer.spring.rest.impl;

import org.springframework.http.ResponseEntity;
import hu.aestallon.storageexplorer.spring.rest.api.ExplorerApiDelegate;
import hu.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionRequest;
import hu.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionResult;
import hu.aestallon.storageexplorer.spring.rest.model.EntryLoadRequest;
import hu.aestallon.storageexplorer.spring.rest.model.EntryLoadResult;
import hu.aestallon.storageexplorer.spring.rest.model.StorageIndexDto;
import hu.aestallon.storageexplorer.spring.service.StorageIndexService;
import hu.aestallon.storageexplorer.spring.util.IndexingMethod;

public class ExplorerApiDelegateImpl implements ExplorerApiDelegate {

  private final StorageIndexService storageIndexService;

  public ExplorerApiDelegateImpl(StorageIndexService storageIndexService) {
    this.storageIndexService = storageIndexService;
  }

  @Override
  public ResponseEntity<EntryAcquisitionResult> getStorageEntry(
      EntryAcquisitionRequest entryAcquisitionRequest) throws Exception {
    return ResponseEntity.ok(storageIndexService.acquire(entryAcquisitionRequest));
  }

  @Override
  public ResponseEntity<StorageIndexDto> getStorageIndex(String method) throws Exception {
    return ResponseEntity.ok(storageIndexService.index(IndexingMethod.parse(method)));
  }

  @Override
  public ResponseEntity<EntryLoadResult> loadStorageEntry(EntryLoadRequest entryLoadRequest)
      throws Exception {
    return ResponseEntity.ok(storageIndexService.load(entryLoadRequest));
  }

}
