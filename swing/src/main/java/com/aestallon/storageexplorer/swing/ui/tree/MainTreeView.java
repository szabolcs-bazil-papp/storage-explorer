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

package com.aestallon.storageexplorer.swing.ui.tree;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.graph.service.GraphExportService;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.event.StorageEntryUserDataChanged;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.common.event.msg.Msg;
import static com.aestallon.storageexplorer.common.util.Streams.enumerationToStream;
import com.aestallon.storageexplorer.core.event.StorageIndexDiscardedEvent;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedListEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedMapEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.swing.ui.dialog.importstorage.ImportStorageController;
import com.aestallon.storageexplorer.swing.ui.dialog.importstorage.ImportStorageDialog;
import com.aestallon.storageexplorer.swing.ui.dialog.loadentry.LoadEntryController;
import com.aestallon.storageexplorer.swing.ui.dialog.loadentry.LoadEntryDialog;
import com.aestallon.storageexplorer.swing.ui.event.StorageInstanceRenamed;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.StorageInstanceStatComponent;
import com.aestallon.storageexplorer.swing.ui.tree.model.StorageTree;
import com.aestallon.storageexplorer.swing.ui.tree.model.node.ClickableTreeNode;
import com.aestallon.storageexplorer.swing.ui.tree.model.node.StorageInstanceTreeNode;
import com.aestallon.storageexplorer.swing.ui.tree.model.node.StorageListTreeNode;
import com.aestallon.storageexplorer.swing.ui.tree.model.node.StorageMapTreeNode;
import com.aestallon.storageexplorer.swing.ui.tree.model.node.StorageObjectTreeNode;

@Component
public class MainTreeView
    extends AbstractTreeView
    <
        StorageTree,
        StorageEntry,
        StorageEntryUserDataChanged,
        StorageInstanceTreeNode,
        ClickableTreeNode,
        MainTreeView>
    implements TreeView
    <
        StorageEntry,
        StorageEntryUserDataChanged> {

  private static final Logger log = LoggerFactory.getLogger(MainTreeView.class);

  private transient StorageEntryTrackingService trackingService;

  public MainTreeView(ApplicationEventPublisher eventPublisher,
                      StorageInstanceProvider storageInstanceProvider,
                      UserConfigService userConfigService,
                      StorageEntryTrackingService trackingService) {
    super(
        eventPublisher, storageInstanceProvider, userConfigService,
        self -> self.trackingService = trackingService);
  }

  @Override
  protected StorageTree initTree() {
    final var tree = StorageTree.create(trackingService);
    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        if (SwingUtilities.isRightMouseButton(e)) {
          final var treePath = tree.getClosestPathForLocation(e.getX(), e.getY());
          if (treePath == null) {
            return;
          }
          final Object lastPathComponent = treePath.getLastPathComponent();
          if (!(lastPathComponent instanceof StorageInstanceTreeNode storageInstanceTreeNode)) {
            return;
          }
          tree.setSelectionPath(treePath);
          final var popup = new StorageIndexNodePopupMenu(storageInstanceTreeNode);
          popup.show(e.getComponent(), e.getX(), e.getY());
        }
      }
    });
    return tree;
  }

  @Override
  protected Class<? extends ClickableTreeNode> entityNodeType() {
    return ClickableTreeNode.class;
  }

  @Override
  public void incorporateNode(final StorageEntry storageEntry) {
    if (treePathsByLeaf.containsKey(storageEntry)) {
      return;
    }

    final StorageInstance storageInstance = storageInstanceProvider.get(storageEntry.storageId());
    final ClickableTreeNode node;
    switch (storageEntry) {
      case ScopedEntry scopedEntry -> {
        final var hostEntry = treePathsByLeaf.keySet().stream()
            .filter(it -> it.uri().getPath().equals(scopedEntry.scope().getPath()))
            .findFirst();
        if (hostEntry.isEmpty()) {
          node = null;
          eventPublisher.publishEvent(Msg.warn(
              "Cannot add orphan scoped entry to Tree!",
              "Entry " + scopedEntry
              + " has been indexed, but will not show on the tree until its host entry is missing."));
        } else {
          final StorageEntry host = hostEntry.get();
          final TreePath hostPath = treePathsByLeaf.get(host);
          node = switch (scopedEntry) {
            case ScopedMapEntry sme -> new StorageMapTreeNode(sme, trackingService);
            case ScopedListEntry sle -> new StorageListTreeNode(sle, trackingService);
            case ScopedObjectEntry soe -> new StorageObjectTreeNode(soe, trackingService);
          };

          final StorageObjectTreeNode hostNode =
              (StorageObjectTreeNode) hostPath.getLastPathComponent();
          if (enumerationToStream(hostNode.children()).anyMatch(
              it -> it instanceof ClickableTreeNode c
                    && c.entity().uri().equals(scopedEntry.uri()))) {
            // node is already here...
            return;
          }

          hostNode.enableChildren(true);
          tree.model().nodeChanged(hostNode);
          tree.model().insertNodeInto(
              (DefaultMutableTreeNode) node,
              hostNode,
              hostNode.getChildCount());

        }
      }
      case ListEntry listEntry -> node = tree.incorporateListEntry(storageInstance, listEntry);
      case ObjectEntry objectEntry ->
          node = tree.incorporateObjectEntry(storageInstance, objectEntry);
      case MapEntry mapEntry -> node = tree.incorporateMapEntry(storageInstance, mapEntry);
      case SequenceEntry sequenceEntry ->
          node = tree.incorporateSequenceEntry(storageInstance, sequenceEntry);
      case null, default -> node = null;
    }

    if (node != null) {
      memoizeTreePathsOf(node);
    }
  }

  @Override
  public void importStorage(final StorageInstance storageInstance) {
    final var storageInstanceTreeNode = tree.importStorage(storageInstance);
    memoizeTreePathsOfStorage(storageInstanceTreeNode);
  }

  @Override
  public void reindexStorage(final StorageInstance storageInstance) {
    final var storageInstanceTreeNode = tree.reindexStorage(storageInstance);
    memoizeTreePathsOfStorage(storageInstanceTreeNode);
  }

  @Override
  public void requestVisibility() {
    // TODO: Implement
  }

  @Override
  public void removeStorage(final StorageInstance storageInstance) {
    tree.removeStorage(storageInstance);
  }

  @Override
  @EventListener
  public void onUserDataChanged(StorageEntryUserDataChanged event) {
    super.onUserDataChanged(event);
  }

  private final class StorageIndexNodePopupMenu extends JPopupMenu {
    private StorageIndexNodePopupMenu(StorageInstanceTreeNode sitn) {
      super(String.valueOf(sitn.getUserObject()));

      final var edit = createEditMenuItem(sitn);
      add(edit);

      if (IndexingStrategyType.ON_DEMAND == sitn.storageInstance().indexingStrategy()) {
        final var loadEntry = createLoadEntryMenuItem(sitn);
        add(loadEntry);
      }


      final var reindex = new JMenuItem("Reload", IconProvider.REFRESH);
      reindex.addActionListener(e -> storageInstanceProvider.reindex(sitn.storageInstance()));
      reindex.setToolTipText(
          "Reload this storage to let the application reflect its current state.");
      add(reindex);

      final var discard = new JMenuItem("Delete", IconProvider.CLOSE);
      discard.addActionListener(e -> eventPublisher.publishEvent(
          new StorageIndexDiscardedEvent(sitn.storageInstance())));
      discard.setToolTipText("Close this storage to reclaim system resources.\n"
                             + "This storage won't be preloaded on the next startup.");
      add(discard);

      final var export = createExportMenuItem(sitn);
      add(export);
      final var stats = createStatsMenuItem(sitn);
      add(stats);
    }

    private JMenuItem createLoadEntryMenuItem(StorageInstanceTreeNode sitn) {
      final var loadEntry = new JMenuItem("Load entry...", IconProvider.DATA_TRANSFER);
      loadEntry.addActionListener(e -> {
        final var controller = LoadEntryController.create(
            sitn.storageInstance(),
            storageInstanceProvider,
            userConfigService);
        final var dialog = new LoadEntryDialog(controller);
        dialog.setLocationRelativeTo(MainTreeView.this);
        dialog.pack();
        dialog.setVisible(true);
      });
      loadEntry.setToolTipText("Provide an exact URI and manually load a sub-graph around it.");
      return loadEntry;
    }

    private JMenuItem createExportMenuItem(StorageInstanceTreeNode sitn) {
      final var export = new JMenuItem("Export", IconProvider.GRAPH);
      export.addActionListener(e -> {
        final var fileChooser = new JFileChooser(FileSystemView.getFileSystemView());
        fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
        fileChooser.setDialogTitle("Save export");

        final int result = fileChooser.showDialog(this, "Export");
        if (JFileChooser.APPROVE_OPTION == result) {
          final File f = fileChooser.getSelectedFile();
          final Path target;
          if (!f.getName().endsWith(".gexf")) {
            String absolutePath = f.getAbsolutePath();
            target = Path.of(absolutePath + ".gexf");
          } else {
            target = f.toPath();
          }

          try {
            new GraphExportService().export(sitn.storageInstance(), target);
          } catch (IOException ex) {
            eventPublisher.publishEvent(Msg.err("Graph Export Failed", ex.getMessage()));
          }
        }
      });
      export.setToolTipText("Export the storage as a GEXF file.");
      return export;
    }

    private JMenuItem createEditMenuItem(StorageInstanceTreeNode sitn) {
      final var edit = new JMenuItem("Edit connection...", IconProvider.EDIT);
      edit.addActionListener(e -> {
        final var controller = ImportStorageController.forUpdating(
            sitn.storageInstance(),
            userConfigService,
            storageInstanceProvider,
            after -> {
              sitn.setUserObject(after.getName());
              tree.model().nodeChanged(sitn);
              eventPublisher.publishEvent(new StorageInstanceRenamed(sitn.storageInstance()));
            });
        final ImportStorageDialog dialog = new ImportStorageDialog(controller);
        dialog.pack();
        dialog.setLocationRelativeTo(MainTreeView.this);
        dialog.setVisible(true);
      });
      edit.setToolTipText("Edit the Storage Instance's name and connection settings.");
      return edit;
    }

    private JMenuItem createStatsMenuItem(StorageInstanceTreeNode sitn) {
      final var stats = new JMenuItem("Statistics");
      stats.addActionListener(e -> {
        final JDialog dialog = new JDialog();
        dialog.setTitle("Statistics");
        dialog.setModal(true);
        dialog.setLocationRelativeTo(MainTreeView.this);
        dialog.getContentPane()
            .add(new StorageInstanceStatComponent(sitn.storageInstance()).asComponent());
        dialog.pack();
        dialog.setVisible(true);
      });
      stats.setToolTipText("Prints basic statistics about the storage instance.");
      return stats;
    }
  }

}
