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

package hu.aestallon.storageexplorer.ui.controller;

import java.net.URI;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.model.instance.StorageInstance;
import hu.aestallon.storageexplorer.ui.ExplorerView;
import hu.aestallon.storageexplorer.ui.GraphView;
import hu.aestallon.storageexplorer.ui.tree.MainTreeView;
import hu.aestallon.storageexplorer.ui.tree.model.node.ClickableTreeNode;

@Service
public class ViewController {

  private static final Logger log = LoggerFactory.getLogger(ViewController.class);


  public record EntryInspectionEvent(StorageEntry storageEntry) {}


  public record GraphRenderingRequest(StorageEntry storageEntry) {}


  public record TreeTouchRequest(StorageEntry storageEntry) {}


  public record GraphSelectionRequest(StorageEntry storageEntry) {}


  public record GraphViewCloseRequest() {}


  public record StorageImportEvent(StorageInstance storageInstance) {}


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
    mainTreeView.selectEntry(e.storageEntry);
  }

  @EventListener
  public void onGraphRenderingRequest(GraphRenderingRequest e) {
    explorerView.openGraphView();
    graphView.init(e.storageEntry);
  }

  @EventListener
  public GraphSelectionRequest onTreeTouchRequest(TreeTouchRequest e) {
    mainTreeView.softSelectEntry(e.storageEntry);

    return new GraphSelectionRequest(e.storageEntry);
  }

  @EventListener
  public void onGraphSelectionRequest(GraphSelectionRequest e) {
    graphView.select(e.storageEntry);
  }

  @EventListener
  public void onGraphViewCloseRequest(GraphViewCloseRequest e) {
    explorerView.closeGraphView();
  }

  @EventListener
  public void onStorageImported(StorageImportEvent e) {
    SwingUtilities.invokeLater(() -> mainTreeView.importStorage(e.storageInstance));
  }

  public record BackgroundWorkStartedEvent(String displayName) {}


  public static final class BackgroundWorkCompletedEvent {
    private enum BackgroundWorkResult { OK, ERR }

    public static BackgroundWorkCompletedEvent ok() {
      return new BackgroundWorkCompletedEvent(BackgroundWorkResult.OK);
    }

    public static BackgroundWorkCompletedEvent err() {
      return new BackgroundWorkCompletedEvent(BackgroundWorkResult.ERR);
    }

    private final BackgroundWorkResult result;

    private BackgroundWorkCompletedEvent(BackgroundWorkResult result) {
      this.result = result;
    }
  }

  @EventListener
  public void onBackgroundWorkStarted(BackgroundWorkStartedEvent e) {
    SwingUtilities.invokeLater(() -> mainTreeView.showProgressBar(e.displayName));
  }

  @EventListener
  public void onBackgroundWorkTerminated(BackgroundWorkCompletedEvent e) {
    SwingUtilities.invokeLater(mainTreeView::removeProgressBar);
  }

  public record StorageReindexed(StorageInstance storageInstance) {}

  @EventListener
  public void onStorageReindexed(StorageReindexed e) {
    SwingUtilities.invokeLater(() -> mainTreeView.reindexStorage(e.storageInstance));
  }

  /**
   * The user wishes to discard a StorageIndex, relinquishing all resources allocated for it.
   *
   * <p>
   * This service will invoke all injected views to respond, by dropping the affected tree node; by
   * clearing, closing and dropping references of the graph view, by closing inspector views.
   *
   * <p>
   * View layer factories and domain services must listen to this event separately, and responding
   * with dropping any and all references to StorageEntries of the discarded {@code StorageIndex},
   * the index itself, its corresponding application context, and so on.
   *
   * <p>
   * References shouldn't be set to {@code null} manually! That just hampers the garbage collector
   * in its efforts to determine whether an object is actually reachable or not.
   */
  public record StorageIndexDiscardedEvent(StorageInstance storageInstance) {}

  @EventListener
  public void onStorageIndexDiscarded(StorageIndexDiscardedEvent e) {
    SwingUtilities.invokeLater(() -> {
      if (graphView.displayingStorageAt(e.storageInstance)) {
        explorerView.closeGraphView();
      }
      explorerView.inspectorContainerView().discardInspectorViewOfStorageAt(e.storageInstance);
      mainTreeView.removeStorageNodeOf(e.storageInstance);
    });
  }

  public record StorageReimportedEvent(StorageInstance storageInstance) {}

  @EventListener
  public void onStorageReimported(StorageReimportedEvent e) {
    SwingUtilities.invokeLater(() -> {
      if (graphView.displayingStorageAt(e.storageInstance)) {
        explorerView.closeGraphView();
      }
      explorerView.inspectorContainerView().discardInspectorViewOfStorageAt(e.storageInstance);
    });
  }

  public record EntryAcquired(StorageInstance storageInstance, StorageEntry storageEntry) {}

  @EventListener
  public void onEntryAcquired(EntryAcquired e) {
    SwingUtilities.invokeLater(() -> {
      log.info("Entry acquired: {}", e.storageEntry);
      mainTreeView.incorporateEntryIntoTree(
          e.storageInstance,
          e.storageEntry);
      log.info("Selecting entry: {}", e.storageEntry);
      mainTreeView.selectEntry(e.storageEntry);
    });
  }

  public record EntryAcquisitionFailed(StorageInstance storageInstance, URI uri) {}

  @EventListener
  public void onEntryAcquisitionFailed(EntryAcquisitionFailed e) {
    SwingUtilities.invokeLater(() -> {
      System.out.println("Failed: " + e.storageInstance + " >>> " + e.uri);
    });
  }

  public record EntryDiscovered(StorageInstance storageInstance, StorageEntry storageEntry) {}

  @EventListener
  public void onEntryDiscovered(EntryDiscovered e) {
    SwingUtilities.invokeLater(() -> mainTreeView.incorporateEntryIntoTree(
        e.storageInstance,
        e.storageEntry));
  }

}
