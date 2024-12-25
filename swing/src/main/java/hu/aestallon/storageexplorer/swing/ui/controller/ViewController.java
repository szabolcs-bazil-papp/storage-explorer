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

package hu.aestallon.storageexplorer.swing.ui.controller;

import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.common.event.msg.ErrorMsg;
import hu.aestallon.storageexplorer.common.event.msg.Msg;
import hu.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkCompletedEvent;
import hu.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkStartedEvent;
import hu.aestallon.storageexplorer.storage.event.EntryAcquired;
import hu.aestallon.storageexplorer.storage.event.EntryAcquisitionFailed;
import hu.aestallon.storageexplorer.storage.event.EntryDiscovered;
import hu.aestallon.storageexplorer.storage.event.EntryInspectionEvent;
import hu.aestallon.storageexplorer.graph.event.GraphRenderingRequest;
import hu.aestallon.storageexplorer.graph.event.GraphSelectionRequest;
import hu.aestallon.storageexplorer.storage.event.StorageImportEvent;
import hu.aestallon.storageexplorer.storage.event.StorageIndexDiscardedEvent;
import hu.aestallon.storageexplorer.storage.event.StorageReimportedEvent;
import hu.aestallon.storageexplorer.storage.event.StorageReindexed;
import hu.aestallon.storageexplorer.storage.event.TreeTouchRequest;
import hu.aestallon.storageexplorer.swing.ui.ExplorerView;
import hu.aestallon.storageexplorer.swing.ui.GraphView;
import hu.aestallon.storageexplorer.swing.ui.tree.MainTreeView;
import hu.aestallon.storageexplorer.swing.ui.tree.model.node.ClickableTreeNode;

@Service
public class ViewController {

  private static final Logger log = LoggerFactory.getLogger(ViewController.class);


  public record GraphViewCloseRequest() {}


  private final ExplorerView explorerView;
  private final MainTreeView mainTreeView;
  private final GraphView graphView;

  public ViewController(ExplorerView explorerView,
                        MainTreeView mainTreeView,
                        GraphView graphView) {
    this.explorerView = explorerView;
    this.mainTreeView = mainTreeView;
    this.graphView = graphView;
  }

  @EventListener
  public GraphSelectionRequest onMainTreeNodeSelected(ClickableTreeNode clickableTreeNode) {
    explorerView.openInspectorContainer();
    explorerView.inspectorContainerView().showInspectorView(clickableTreeNode.storageEntry());

    return new GraphSelectionRequest(clickableTreeNode.storageEntry());
  }

  @EventListener
  public void onEntryInspected(EntryInspectionEvent e) {
    mainTreeView.selectEntry(e.storageEntry());
  }

  @EventListener
  public void onGraphRenderingRequest(GraphRenderingRequest e) {
    explorerView.openGraphView();
    graphView.init(e.storageEntry());
  }

  @EventListener
  public GraphSelectionRequest onTreeTouchRequest(TreeTouchRequest e) {
    mainTreeView.softSelectEntry(e.storageEntry());

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
  public void onBackgroundWorkStarted(BackgroundWorkStartedEvent e) {
    SwingUtilities.invokeLater(() -> mainTreeView.showProgressBar(e.displayName()));
  }

  @EventListener
  public void onBackgroundWorkTerminated(BackgroundWorkCompletedEvent e) {
    SwingUtilities.invokeLater(mainTreeView::removeProgressBar);
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
      mainTreeView.removeStorageNodeOf(e.storageInstance());
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
      mainTreeView.incorporateEntryIntoTree(
          e.storageInstance(),
          e.storageEntry());
      log.info("Selecting entry: {}", e.storageEntry());
      mainTreeView.selectEntry(e.storageEntry());
    });
  }

  @EventListener
  public ErrorMsg onEntryAcquisitionFailed(EntryAcquisitionFailed e) {
    return Msg.err("Failed to retrieve entry",
        e.uri() + " is not available in storage: " + e.storageInstance().name());
  }

  @EventListener
  public void onEntryDiscovered(EntryDiscovered e) {
    SwingUtilities.invokeLater(() -> mainTreeView.incorporateEntryIntoTree(
        e.storageInstance(),
        e.storageEntry()));
  }

}
