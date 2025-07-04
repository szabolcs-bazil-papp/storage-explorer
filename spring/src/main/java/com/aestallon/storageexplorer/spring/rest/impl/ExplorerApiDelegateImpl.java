package com.aestallon.storageexplorer.spring.rest.impl;

import java.util.List;
import org.springframework.http.ResponseEntity;
import com.aestallon.storageexplorer.spring.rest.api.ExplorerApiDelegate;
import com.aestallon.storageexplorer.spring.rest.model.ArcScriptEvalError;
import com.aestallon.storageexplorer.spring.rest.model.ArcScriptEvalRequest;
import com.aestallon.storageexplorer.spring.rest.model.ArcScriptEvalResponse;
import com.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionRequest;
import com.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionResult;
import com.aestallon.storageexplorer.spring.rest.model.EntryLoadRequest;
import com.aestallon.storageexplorer.spring.rest.model.EntryLoadResult;
import com.aestallon.storageexplorer.spring.rest.model.StorageIndexDto;
import com.aestallon.storageexplorer.spring.service.StorageIndexService;
import com.aestallon.storageexplorer.spring.util.IndexingMethod;

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

  @Override
  public ResponseEntity<ArcScriptEvalResponse> eval(ArcScriptEvalRequest arcScriptEvalRequest)
      throws Exception {
    return switch (storageIndexService.evalArcScript(arcScriptEvalRequest.getScript())) {
      case StorageIndexService.ArcScriptQueryEvalResult.Ok(List<Object> resultSet) ->
          ResponseEntity.ok(new ArcScriptEvalResponse(resultSet));
      case StorageIndexService.ArcScriptQueryEvalResult.Err(ArcScriptEvalError err) ->
          ResponseEntity.badRequest().body(new ArcScriptEvalResponse().err(err));
    };
  }
}
