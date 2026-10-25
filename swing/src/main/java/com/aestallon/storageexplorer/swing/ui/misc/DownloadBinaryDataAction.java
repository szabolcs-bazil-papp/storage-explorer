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

import java.awt.event.ActionEvent;
import java.net.URI;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public class DownloadBinaryDataAction extends AbstractAction {

  private final StorageInstance storageInstance;
  private final URI uri;
  private final JComponent parent;

  public DownloadBinaryDataAction(final StorageInstance storageInstance,
                                  final URI uri,
                                  final JComponent parent) {
    super(null, IconProvider.SAVE);
    this.storageInstance = storageInstance;
    this.uri = uri;
    this.parent = parent;
    putValue(SHORT_DESCRIPTION, "Download this entry's binary content to a file");
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    BinaryDataDownloader.download(storageInstance, uri, parent);
  }

}
