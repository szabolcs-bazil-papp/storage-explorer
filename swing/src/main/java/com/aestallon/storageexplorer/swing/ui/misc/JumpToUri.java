package com.aestallon.storageexplorer.swing.ui.misc;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.common.util.Uris;
import com.aestallon.storageexplorer.core.event.EntryAcquired;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.StorageInstanceProvider;

public final class JumpToUri {

  private JumpToUri() {}

  public static void jump(ApplicationEventPublisher eventPublisher,
                          URI uri,
                          StorageInstanceProvider storageInstanceProvider,
                          StorageId storageId) {
    StorageInstance storageInstance = storageInstanceProvider.get(storageId);
    jump(eventPublisher, uri, storageInstance);
  }

  public static void jump(ApplicationEventPublisher eventPublisher,
                          URI uri,
                          StorageInstance storageInstance) {
    CompletableFuture.runAsync(() -> storageInstance
        .acquire(Uris.latest(uri))
        .ifPresentOrElse(
            it -> eventPublisher.publishEvent(new EntryAcquired(storageInstance, it)),
            () -> SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                null,
                "Cannot show URI: " + uri,
                "Unreachable URI",
                JOptionPane.ERROR_MESSAGE))));
  }

}
