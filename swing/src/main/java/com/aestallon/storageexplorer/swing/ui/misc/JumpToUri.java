package com.aestallon.storageexplorer.swing.ui.misc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.event.EntryAcquired;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.util.Uris;
import com.formdev.flatlaf.util.SystemFileChooser;

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
    final URI latestUri = Uris.latest(uri);
    if (Uris.isBinaryData(latestUri)) {
      saveBinaryData(storageInstance, latestUri);
      return;
    }

    CompletableFuture.runAsync(() -> storageInstance
        .acquire(latestUri)
        .ifPresentOrElse(
            it -> eventPublisher.publishEvent(new EntryAcquired(storageInstance, it)),
            () -> SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                null,
                "Cannot show URI: " + uri,
                "Unreachable URI",
                JOptionPane.ERROR_MESSAGE))));
  }

  private static void saveBinaryData(final StorageInstance storageInstance, final URI uri) {
    final var fileChooser = new SystemFileChooser();
    fileChooser.setDialogTitle("Save binary data");
    fileChooser.setSelectedFile(new File(Uris.getUuid(uri)));
    final int result = fileChooser.showSaveDialog(null);
    if (SystemFileChooser.APPROVE_OPTION != result) {
      return;
    }

    final File target = fileChooser.getSelectedFile();
    CompletableFuture.runAsync(() -> storageInstance
        .loadBinaryData(uri)
        .ifPresentOrElse(
            is -> writeBinaryData(is, target, uri),
            () -> SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                null,
                "Cannot load binary data: " + uri,
                "Unreachable URI",
                JOptionPane.ERROR_MESSAGE))));
  }

  private static void writeBinaryData(final InputStream is,
                                      final File target,
                                      final URI uri) {
    try (is) {
      Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (final IOException e) {
      SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
          null,
          "Could not save binary data of [ " + uri + " ]: " + e.getMessage(),
          "Save failed",
          JOptionPane.ERROR_MESSAGE));
    }
  }

}
