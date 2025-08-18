package com.aestallon.storageexplorer.spring.rest.impl;

import org.springframework.http.ResponseEntity;
import com.aestallon.storageexplorer.spring.auth.AuthService;
import com.aestallon.storageexplorer.spring.rest.api.ExplorerApiDelegate;
import com.aestallon.storageexplorer.spring.rest.model.ArcScriptEvalError;
import com.aestallon.storageexplorer.spring.rest.model.ArcScriptEvalRequest;
import com.aestallon.storageexplorer.spring.rest.model.ArcScriptEvalResponse;
import com.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionRequest;
import com.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionResult;
import com.aestallon.storageexplorer.spring.rest.model.EntryLoadRequest;
import com.aestallon.storageexplorer.spring.rest.model.EntryLoadResult;
import com.aestallon.storageexplorer.spring.rest.model.LoginData;
import com.aestallon.storageexplorer.spring.rest.model.LoginResult;
import com.aestallon.storageexplorer.spring.rest.model.StorageIndexDto;
import com.aestallon.storageexplorer.spring.service.StorageIndexService;
import com.aestallon.storageexplorer.spring.util.IndexingMethod;

public class ExplorerApiDelegateImpl implements ExplorerApiDelegate {

  private final StorageIndexService storageIndexService;
  private final AuthService authService;

  public ExplorerApiDelegateImpl(StorageIndexService storageIndexService, AuthService authService) {
    this.storageIndexService = storageIndexService;
    this.authService = authService;
  }

  @Override
  public ResponseEntity<LoginResult> verify(LoginData loginData) throws Exception {
    final var token = authService.issueToken(loginData.getUsername(), loginData.getPassword());
    return (token != null)
        ? ResponseEntity.ok(new LoginResult().token(token))
        : ResponseEntity.badRequest().body(new LoginResult().err("Invalid credentials"));
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
      case StorageIndexService.ArcScriptQueryEvalResult.Ok(var cols, var resultSet) ->
          ResponseEntity.ok(new ArcScriptEvalResponse(cols, "uri", resultSet));
      case StorageIndexService.ArcScriptQueryEvalResult.Err(ArcScriptEvalError err) ->
          ResponseEntity.badRequest().body(new ArcScriptEvalResponse().err(err));
    };
  }
}
