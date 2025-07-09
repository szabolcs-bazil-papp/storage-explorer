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

package com.aestallon.storageexplorer.swing.ui.explorer;

import java.awt.*;
import java.util.EnumSet;
import javax.swing.*;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.swing.ui.graph.GraphView;

@Component
public class ExplorerView extends JPanel {

  public enum DisplayMode { GRAPH_ONLY, INSPECTORS_ONLY, BOTH, NONE }


  private DisplayMode displayMode;

  private JSplitPane contentPane;

  private final GraphView graphView;
  private final InspectorContainerView inspectorContainerView;
  private final transient StorageEntryTrackingService trackingService;

  public ExplorerView(GraphView graphView,
                      InspectorContainerView inspectorContainerView,
                      StorageEntryTrackingService trackingService) {
    super(new BorderLayout());
    this.graphView = graphView;
    this.inspectorContainerView = inspectorContainerView;
    this.trackingService = trackingService;

    displayMode = DisplayMode.NONE;
  }

  private boolean inspectorContainerOpen() {
    return EnumSet.of(DisplayMode.BOTH, DisplayMode.INSPECTORS_ONLY).contains(displayMode);
  }

  private boolean graphViewOpen() {
    return EnumSet.of(DisplayMode.BOTH, DisplayMode.GRAPH_ONLY).contains(displayMode);
  }

  public void openInspectorContainer() {
    if (inspectorContainerOpen()) {
      return;
    }

    if (graphViewOpen()) {
      remove(graphView);
      initContentPane();
      add(contentPane);

      displayMode = DisplayMode.BOTH;
    } else {
      add(inspectorContainerView);
      displayMode = DisplayMode.INSPECTORS_ONLY;
    }
    revalidate();
  }

  public void openGraphView() {
    if (graphViewOpen()) {
      return;
    }

    if (inspectorContainerOpen()) {
      remove(inspectorContainerView);
      initContentPane();
      add(contentPane);

      displayMode = DisplayMode.BOTH;
    } else {
      add(graphView);
      displayMode = DisplayMode.GRAPH_ONLY;
    }
    revalidate();
  }

  public void closeGraphView() {
    if (!graphViewOpen()) {
      return;
    }
    graphView.discard();
    if (inspectorContainerOpen()) {
      remove(contentPane);
      add(inspectorContainerView);

      displayMode = DisplayMode.INSPECTORS_ONLY;
    } else {
      remove(graphView);
      displayMode = DisplayMode.NONE;
    }
    revalidate();
  }

  public void reopenTrackedEntryInspectors() {
    final var entries = trackingService.entriesOfTrackedInspectors();
    if (entries.isEmpty()) {
      return;
    }

    if (!inspectorContainerOpen()) {
      openInspectorContainer();
    }

    entries.forEach(inspectorContainerView::showInspectorView);
  }

  private void initContentPane() {
    contentPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graphView, inspectorContainerView);
    contentPane.getLeftComponent().setMinimumSize(new Dimension(500, 250));
  }

  public DisplayMode displayMode() {
    return displayMode;
  }

  public GraphView graphView() {
    return graphView;
  }

  public InspectorContainerView inspectorContainerView() {
    return inspectorContainerView;
  }



}
