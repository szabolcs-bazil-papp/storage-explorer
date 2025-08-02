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

package com.aestallon.storageexplorer.swing.ui.controller;

import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.graph.event.GraphRenderingRequest;
import com.aestallon.storageexplorer.client.graph.event.GraphSelectionRequest;
import com.aestallon.storageexplorer.client.graph.event.GraphState;
import com.aestallon.storageexplorer.client.userconfig.service.StoredArcScript;
import com.aestallon.storageexplorer.common.event.msg.ErrorMsg;
import com.aestallon.storageexplorer.common.event.msg.Msg;
import com.aestallon.storageexplorer.core.event.EntryAcquired;
import com.aestallon.storageexplorer.core.event.EntryAcquisitionFailed;
import com.aestallon.storageexplorer.core.event.EntryDiscovered;
import com.aestallon.storageexplorer.core.event.EntryInspectionEvent;
import com.aestallon.storageexplorer.core.event.StorageImportEvent;
import com.aestallon.storageexplorer.core.event.StorageIndexDiscardedEvent;
import com.aestallon.storageexplorer.core.event.StorageReimportedEvent;
import com.aestallon.storageexplorer.core.event.StorageReindexed;
import com.aestallon.storageexplorer.core.event.TreeTouchRequest;
import com.aestallon.storageexplorer.swing.ui.AppContentView;
import com.aestallon.storageexplorer.swing.ui.explorer.ExplorerView;
import com.aestallon.storageexplorer.swing.ui.graph.GraphView;
import com.aestallon.storageexplorer.swing.ui.tree.MainTreeView;
import com.aestallon.storageexplorer.swing.ui.tree.model.node.ClickableTreeNode;

@Service
public class ViewController {

  private static final Logger log = LoggerFactory.getLogger(ViewController.class);


  public record GraphViewCloseRequest() {}


  private final AppContentView appContentView;
  private final ExplorerView explorerView;
  private final MainTreeView mainTreeView;
  private final GraphView graphView;

  public ViewController(AppContentView appContentView, ExplorerView explorerView,
                        MainTreeView mainTreeView,
                        GraphView graphView) {
    this.appContentView = appContentView;
    this.explorerView = explorerView;
    this.mainTreeView = mainTreeView;
    this.graphView = graphView;
  }

  @EventListener
  public GraphSelectionRequest onMainTreeNodeSelected(ClickableTreeNode clickableTreeNode) {
    explorerView.openInspectorContainer();
    explorerView.inspectorContainerView().showInspectorView(clickableTreeNode.entity());

    return new GraphSelectionRequest(clickableTreeNode.entity());
  }

  @EventListener
  public void onEntryInspected(EntryInspectionEvent e) {
    mainTreeView.selectNode(e.storageEntry());
  }

  @EventListener
  public void onGraphRenderingRequest(GraphRenderingRequest e) {
    explorerView.openGraphView();
    graphView.init(e.storageEntry());
  }

  @EventListener
  public GraphSelectionRequest onTreeTouchRequest(TreeTouchRequest e) {
    mainTreeView.selectNodeSoft(e.storageEntry());

    return new GraphSelectionRequest(e.storageEntry());
  }

  @EventListener
  public void onGraphSelectionRequest(GraphSelectionRequest e) {
    graphView.select(e.storageEntry());
  }

  @EventListener
  public void onGraphViewCloseRequest(GraphViewCloseRequest e) {
    explorerView.closeGraphView();
  }

  @EventListener
  public void onStorageImported(StorageImportEvent e) {
    SwingUtilities.invokeLater(() -> mainTreeView.importStorage(e.storageInstance()));
  }

  @EventListener
  public void onStorageReindexed(StorageReindexed e) {
    SwingUtilities.invokeLater(() -> mainTreeView.reindexStorage(e.storageInstance()));
  }

  @EventListener
  public void onStorageIndexDiscarded(StorageIndexDiscardedEvent e) {
    SwingUtilities.invokeLater(() -> {
      if (graphView.displayingStorageAt(e.storageInstance())) {
        explorerView.closeGraphView();
      }
      explorerView.inspectorContainerView().discardInspectorViewOfStorageAt(e.storageInstance());
      mainTreeView.removeStorage(e.storageInstance());
    });
  }

  @EventListener
  public void onStorageReimported(StorageReimportedEvent e) {
    SwingUtilities.invokeLater(() -> {
      if (graphView.displayingStorageAt(e.storageInstance())) {
        explorerView.closeGraphView();
      }
      explorerView.inspectorContainerView().discardInspectorViewOfStorageAt(e.storageInstance());
    });
  }

  @EventListener
  public void onEntryAcquired(EntryAcquired e) {
    SwingUtilities.invokeLater(() -> {
      log.info("Entry acquired: {}", e.storageEntry());
      mainTreeView.incorporateNode(e.storageEntry());
      log.info("Selecting entry: {}", e.storageEntry());
      mainTreeView.selectNode(e.storageEntry());
    });
  }

  @EventListener
  public ErrorMsg onEntryAcquisitionFailed(EntryAcquisitionFailed e) {
    return Msg.err("Failed to retrieve entry",
        e.uri() + " is not available in storage: " + e.storageInstance().name());
  }

  @EventListener
  public void onEntryDiscovered(EntryDiscovered e) {
    SwingUtilities.invokeLater(
        () -> mainTreeView.incorporateNode(e.storageEntry()));
  }

  @EventListener
  public void onGraphStateChanged(GraphState e) {
    SwingUtilities.invokeLater(() -> appContentView.setGraphState(e));
  }

  public record ArcScriptTreeTouchRequest(StoredArcScript sas) {}

  @EventListener
  public void onArcScriptTreeTouchRequested(ArcScriptTreeTouchRequest e) {
    log.info("e");
  }

}
