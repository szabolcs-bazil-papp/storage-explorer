/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.swing.ui.misc;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.util.Uris;
import com.formdev.flatlaf.util.SystemFileChooser;

/**
 * Lets the user pick a destination file through a {@link SystemFileChooser}, then streams a
 * {@code BinaryDataObject}'s content from the given {@link StorageInstance} into it, reporting
 * success or failure once the transfer completes.
 */
public final class BinaryDataDownloader {

  private BinaryDataDownloader() {}

  public static void download(final StorageInstance storageInstance,
                              final URI uri,
                              final Component parent) {
    final var fileChooser = new SystemFileChooser();
    fileChooser.setDialogTitle("Save binary data");
    fileChooser.setSelectedFile(new File(Uris.getUuid(uri)));
    final int result = fileChooser.showSaveDialog(parent);
    if (SystemFileChooser.APPROVE_OPTION != result) {
      return;
    }

    final File target = fileChooser.getSelectedFile();
    CompletableFuture.runAsync(() -> storageInstance
        .loadBinaryData(uri)
        .ifPresentOrElse(
            is -> writeBinaryData(is, target, uri, parent),
            () -> SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                parent,
                "Cannot load binary data: " + uri,
                "Unreachable URI",
                JOptionPane.ERROR_MESSAGE))));
  }

  private static void writeBinaryData(final InputStream is,
                                      final File target,
                                      final URI uri,
                                      final Component parent) {
    try (is) {
      Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
      SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
          parent,
          "Saved binary data to " + target.getAbsolutePath(),
          "Save successful",
          JOptionPane.INFORMATION_MESSAGE));
    } catch (final IOException e) {
      SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
          parent,
          "Could not save binary data of [ " + uri + " ]: " + e.getMessage(),
          "Save failed",
          JOptionPane.ERROR_MESSAGE));
    }
  }

}
