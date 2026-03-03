package com.aestallon.storageexplorer.client.userconfig.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.event.TypeInfoUpdated;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import jakarta.annotation.PreDestroy;

@Component
public class TypeInfoUpdatedListener {

  private static final long DEBOUNCE_DELAY_MS = 2_000L;
  private static final Logger log = LoggerFactory.getLogger(TypeInfoUpdatedListener.class);

  private final StorageInstanceProvider storageInstanceProvider;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final ConcurrentHashMap<StorageId, ScheduledFuture<?>> pendingTasks =
      new ConcurrentHashMap<>();

  public TypeInfoUpdatedListener(StorageInstanceProvider storageInstanceProvider) {
    this.storageInstanceProvider = storageInstanceProvider;
  }

  @PreDestroy
  public void shutdown() {
    scheduler.shutdownNow();
  }

  @EventListener(TypeInfoUpdated.class)
  public void onTypeInfoUpdated(TypeInfoUpdated e) {
    final StorageId storageId = e.storageId();

    final var existingFuture = pendingTasks.get(storageId);
    if (existingFuture != null) {
      existingFuture.cancel(false);
    }

    final var next = scheduler.schedule(
        () -> persistTypeInfo(storageId),
        DEBOUNCE_DELAY_MS,
        TimeUnit.MILLISECONDS);
    pendingTasks.put(storageId, next);
  }

  private void persistTypeInfo(StorageId storageId) {
    try {
      storageInstanceProvider.saveTypeInformation(storageId);
    } catch (final Exception e) {
      log.error("Failed to persist type info for storage {}", storageId, e);
    } finally {
      pendingTasks.remove(storageId);
    }
  }
}
