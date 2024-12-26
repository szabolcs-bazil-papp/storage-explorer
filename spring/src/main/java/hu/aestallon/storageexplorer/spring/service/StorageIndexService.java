package hu.aestallon.storageexplorer.spring.service;

import hu.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionRequest;
import hu.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionResult;
import hu.aestallon.storageexplorer.spring.rest.model.EntryLoadRequest;
import hu.aestallon.storageexplorer.spring.rest.model.EntryLoadResult;
import hu.aestallon.storageexplorer.spring.rest.model.StorageIndexDto;
import hu.aestallon.storageexplorer.spring.util.IndexingMethod;

public class StorageIndexService {

  private final StorageIndexProvider indexProvider;

  public StorageIndexService(StorageIndexProvider indexProvider) {
    this.indexProvider = indexProvider;
  }
  
  public StorageIndexDto index(final IndexingMethod indexingMethod) {
    
    return new StorageIndexDto();
  }
  
  public EntryAcquisitionResult acquire(final EntryAcquisitionRequest acquisitionRequest) {
    
    return new EntryAcquisitionResult();
  }
  
  public EntryLoadResult load(final EntryLoadRequest loadRequest) {
    
    return new EntryLoadResult();
  }
  
}
