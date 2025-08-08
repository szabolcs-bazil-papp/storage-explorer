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

package com.aestallon.storageexplorer.swing.ui.arcscript;

import java.net.URI;
import javax.swing.*;
import com.aestallon.storageexplorer.swing.ui.misc.JLink;

public final class LearnMoreView extends JPanel {
  private static final URI URI_WIKI =
      URI.create("https://github.com/szabolcs-bazil-papp/storage-explorer/wiki/ArcScript");

  public LearnMoreView() {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

    final var label = new JLabel("The learn more about ArcScript, visit the wiki ");
    add(label);

    final var btn = new JLink("here", URI_WIKI);
    add(btn);
  }
}
