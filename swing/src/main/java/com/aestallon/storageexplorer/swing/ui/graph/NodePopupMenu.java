/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package com.aestallon.storageexplorer.swing.ui.graph;

import javax.swing.*;
import org.graphstream.graph.Graph;
import com.aestallon.storageexplorer.client.graph.service.GraphRenderingService;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public class NodePopupMenu extends JPopupMenu {

  private final StorageEntry storageEntry;
  private final Graph graph;
  private final GraphRenderingService graphRenderingService;

  public NodePopupMenu(StorageEntry storageEntry, Graph graph,
                       GraphRenderingService graphRenderingService) {
    super(storageEntry.uri().toString());

    this.storageEntry = storageEntry;
    this.graph = graph;
    this.graphRenderingService = graphRenderingService;

    add(title());
    addSeparator();
    add(loadMoreMenuItem());
  }

  private JLabel title() {
    final var item = new JLabel(String.format("<html><strong>%s</strong></html>", getLabel()));
    item.setHorizontalAlignment(SwingConstants.CENTER);
    return item;
  }

  private JMenuItem loadMoreMenuItem() {
    final var item = new JMenuItem("Load more");
    item.addActionListener(e -> graphRenderingService.render(graph, storageEntry));
    return item;
  }

}
