package com.aestallon.storageexplorer.spring.service;

import com.aestallon.storageexplorer.core.service.engine.StorageEngine;

/** Builds a fresh {@link StorageEngine} for the storage the host application has configured —
 *  called once per {@code StorageIndexService} request, matching the new architecture's
 *  transient, operation-scoped philosophy (nothing is cached across calls, same as before this
 *  rework, just at a lower level now: an engine, not a whole eager index). */
public interface StorageIndexProvider {

  StorageEngine provide();

}
