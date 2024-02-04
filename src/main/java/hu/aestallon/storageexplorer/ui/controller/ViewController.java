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

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import hu.aestallon.storageexplorer.model.tree.Clickable;
import hu.aestallon.storageexplorer.ui.ExplorerView;
import hu.aestallon.storageexplorer.ui.GraphView;
import hu.aestallon.storageexplorer.ui.MainTreeView;

@Service
public class ViewController {

  public static final class EntryInspectionEvent {
    private final StorageEntry storageEntry;

    public EntryInspectionEvent(StorageEntry storageEntry) {
      this.storageEntry = storageEntry;
    }

  }


  public static final class GraphRenderingRequest {
    private final StorageEntry storageEntry;

    public GraphRenderingRequest(StorageEntry storageEntry) {
      this.storageEntry = storageEntry;
    }

  }


  public static final class TreeTouchRequest {
    private final StorageEntry storageEntry;

    public TreeTouchRequest(StorageEntry storageEntry) {this.storageEntry = storageEntry;}
  }


  public static final class GraphSelectionRequest {
    private final StorageEntry storageEntry;

    public GraphSelectionRequest(StorageEntry storageEntry) {
      this.storageEntry = storageEntry;
    }

  }


  public static final class GraphViewCloseRequest {
  }


  private final ExplorerView explorerView;
  private final MainTreeView mainTreeView;
  private final GraphView graphView;

  public ViewController(StorageIndex storageIndex, ExplorerView explorerView,
                        MainTreeView mainTreeView,
                        GraphView graphView) {
    this.explorerView = explorerView;
    this.mainTreeView = mainTreeView;
    this.graphView = graphView;
  }

  @EventListener
  public GraphSelectionRequest onMainTreeNodeSelected(Clickable clickable) {
    explorerView.openInspectorContainer();
    explorerView.inspectorContainerView().showInspectorView(clickable.storageEntry());

    return new GraphSelectionRequest(clickable.storageEntry());
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

}
