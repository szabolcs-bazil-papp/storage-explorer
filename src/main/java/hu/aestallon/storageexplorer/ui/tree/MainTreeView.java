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

package hu.aestallon.storageexplorer.ui.tree;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ListEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.MapEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedListEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedMapEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.SequenceEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.model.instance.StorageInstance;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.IndexingStrategyType;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndexProvider;
import hu.aestallon.storageexplorer.domain.userconfig.service.UserConfigService;
import hu.aestallon.storageexplorer.ui.AppFrame;
import hu.aestallon.storageexplorer.ui.controller.ViewController;
import hu.aestallon.storageexplorer.ui.dialog.importstorage.ImportStorageController;
import hu.aestallon.storageexplorer.ui.dialog.importstorage.ImportStorageDialog;
import hu.aestallon.storageexplorer.ui.dialog.loadentry.LoadEntryController;
import hu.aestallon.storageexplorer.ui.dialog.loadentry.LoadEntryDialog;
import hu.aestallon.storageexplorer.ui.misc.IconProvider;
import hu.aestallon.storageexplorer.ui.tree.model.StorageTree;
import hu.aestallon.storageexplorer.ui.tree.model.node.ClickableTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageInstanceTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageListTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageMapTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageObjectTreeNode;
import hu.aestallon.storageexplorer.util.Pair;
import static hu.aestallon.storageexplorer.util.Streams.enumerationToStream;

@Component
public class MainTreeView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(MainTreeView.class);

  private StorageTree tree;
  private JScrollPane treePanel;
  private JProgressBar progressBar;

  private Map<StorageEntry, TreePath> treePathByEntry;

  private final AtomicBoolean propagate = new AtomicBoolean(true);
  private final ApplicationEventPublisher eventPublisher;
  private final StorageIndexProvider storageIndexProvider;
  private final UserConfigService userConfigService;

  public MainTreeView(ApplicationEventPublisher eventPublisher,
                      StorageIndexProvider storageIndexProvider,
                      UserConfigService userConfigService) {
    this.eventPublisher = eventPublisher;
    this.storageIndexProvider = storageIndexProvider;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setPreferredSize(new Dimension(300, 500));

    initTree();
    treePanel = new JScrollPane(tree);
    add(treePanel);
    this.userConfigService = userConfigService;
  }

  private void initTree() {
    tree = StorageTree.create();
    tree.addTreeSelectionListener(e -> {
      final var treeNode = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
      if (treeNode instanceof ClickableTreeNode && propagate.get()) {
        eventPublisher.publishEvent(treeNode);
      }
    });
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
          if (!(lastPathComponent instanceof StorageInstanceTreeNode)) {
            return;
          }
          tree.setSelectionPath(treePath);
          final var storageInstanceTreeNode = (StorageInstanceTreeNode) lastPathComponent;
          final var popup = new StorageIndexNodePopupMenu(storageInstanceTreeNode);
          popup.show(e.getComponent(), e.getX(), e.getY());
        }
      }
    });

    treePathByEntry = new HashMap<>();
  }

  public void incorporateEntryIntoTree(final StorageInstance storageInstance,
                                       final StorageEntry storageEntry) {
    @SuppressWarnings({ "unused" })
    final ClickableTreeNode node;
    if (storageEntry instanceof ScopedEntry) {
      final var scopedEntry = (ScopedEntry) storageEntry;
      final var hostEntry = treePathByEntry.keySet().stream()
          .filter(it -> it.uri().getPath().equals(scopedEntry.scope().getPath()))
          .findFirst();
      if (hostEntry.isEmpty()) {
        node = null;
        log.warn("Failed to install scoped entry: {}", scopedEntry);
      } else {
        final StorageEntry host = hostEntry.get();
        TreePath hostPath = treePathByEntry.get(host);
        if (scopedEntry instanceof ScopedMapEntry) {
          node = new StorageMapTreeNode((ScopedMapEntry) scopedEntry);
        } else if (scopedEntry instanceof ScopedListEntry) {
          node = new StorageListTreeNode((ScopedListEntry) scopedEntry);
        } else if (scopedEntry instanceof ScopedObjectEntry) {
          node = new StorageObjectTreeNode((ScopedObjectEntry) scopedEntry);
        } else {
          throw new AssertionError("Unexpected scoped entry type: " + scopedEntry);
        }

        final StorageObjectTreeNode hostNode =
            (StorageObjectTreeNode) hostPath.getLastPathComponent();
        if (enumerationToStream(hostNode.children()).anyMatch(
            it -> it instanceof ClickableTreeNode && ((ClickableTreeNode) it).storageEntry().uri()
                .equals(scopedEntry.uri()))) {
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
    } else if (storageEntry instanceof ListEntry) {
      node = tree.incorporateListEntry(storageInstance, (ListEntry) storageEntry);
    } else if (storageEntry instanceof ObjectEntry) {
      node = tree.incorporateObjectEntry(storageInstance, (ObjectEntry) storageEntry);
    } else if (storageEntry instanceof MapEntry) {
      node = tree.incorporateMapEntry(storageInstance, (MapEntry) storageEntry);
    } else if (storageEntry instanceof SequenceEntry) {
      node = tree.incorporateSequenceEntry(storageInstance, (SequenceEntry) storageEntry);
    } else {
      node = null;
    }

    if (node != null) {
      memoizeTreePathsOf(tree.nodeOf(storageInstance));
    }
  }

  public void importStorage(final StorageInstance storageInstance) {
    final var storageInstanceTreeNode = tree.importStorage(storageInstance);
    memoizeTreePathsOf(storageInstanceTreeNode);
  }

  private void memoizeTreePathsOf(StorageInstanceTreeNode storageInstanceTreeNode) {
    final var newTreePaths = Stream.of(storageInstanceTreeNode)
        .flatMap(MainTreeView::flatten)
        .filter(ClickableTreeNode.class::isInstance)

        .map(it -> Pair.of(
            ((ClickableTreeNode) it).storageEntry(),
            new TreePath(it.getPath())))
        .collect(Pair.toMap());
    treePathByEntry.putAll(newTreePaths);
  }

  public void reindexStorage(final StorageInstance storageInstance) {
    final var storageInstanceTreeNode = tree.reindexStorage(storageInstance);
    memoizeTreePathsOf(storageInstanceTreeNode);
  }


  private static Stream<DefaultMutableTreeNode> flatten(DefaultMutableTreeNode node) {
    return Stream.concat(
        Stream.of(node),
        enumerationToStream(node.children())
            .filter(DefaultMutableTreeNode.class::isInstance)
            .map(DefaultMutableTreeNode.class::cast)
            .flatMap(MainTreeView::flatten));
  }

  public void selectEntry(StorageEntry storageEntry) {
    Optional
        .ofNullable(treePathByEntry.get(storageEntry))
        .ifPresent(path -> {
          tree.setSelectionPath(path);
          tree.scrollPathToVisible(path);
        });
  }

  public void softSelectEntry(final StorageEntry storageEntry) {
    propagate.set(false);  // FIXME: This is a freaking hack!
    selectEntry(storageEntry);
    propagate.set(true);
  }

  public void removeStorageNodeOf(final StorageInstance storageInstance) {
    tree.removeStorage(storageInstance);
  }

  public void showProgressBar(final String displayName) {
    if (progressBar == null) {
      progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
      progressBar.setPreferredSize(new Dimension(300, 20));
    }
    progressBar.setString(displayName);
    progressBar.setStringPainted(true);
    progressBar.setIndeterminate(true);
    add(progressBar);
    revalidate();
  }

  public void removeProgressBar() {
    if (progressBar == null) {
      return;
    }
    remove(progressBar);
    revalidate();
  }

  private final class StorageIndexNodePopupMenu extends JPopupMenu {
    private StorageIndexNodePopupMenu(StorageInstanceTreeNode sitn) {
      super(String.valueOf(sitn.getUserObject()));

      final var edit = createEditMenuItem(sitn);
      add(edit);

      if (IndexingStrategyType.ON_DEMAND == sitn.storageInstance().indexingStrategy()) {
        final var loadEntry = new JMenuItem("Load entry...", IconProvider.DATA_TRANSFER);
        loadEntry.addActionListener(e -> {
          final var dialog = LoadEntryController.newDialog(sitn.storageInstance());
          dialog.setLocationRelativeTo(MainTreeView.this);
          dialog.pack();
          dialog.setVisible(true);
        });
        loadEntry.setToolTipText("Provide an exact URI and manually load a sub-graph around it.");
        add(loadEntry);
      }


      final var reindex = new JMenuItem("Reload", IconProvider.REFRESH);
      reindex.addActionListener(e -> storageIndexProvider.reindex(sitn.storageInstance()));
      reindex.setToolTipText(
          "Reload this storage to let the application reflect its current state.");
      add(reindex);

      final var discard = new JMenuItem("Delete", IconProvider.CLOSE);
      discard.addActionListener(e -> eventPublisher.publishEvent(
          new ViewController.StorageIndexDiscardedEvent(sitn.storageInstance())));
      discard.setToolTipText("Close this storage to reclaim system resources.\n"
          + "This storage won't be preloaded on the next startup.");
      add(discard);
    }

    private JMenuItem createEditMenuItem(StorageInstanceTreeNode sitn) {
      final var edit = new JMenuItem("Edit connection...", IconProvider.EDIT);
      edit.addActionListener(e -> {
        final var controller = ImportStorageController.forUpdating(
            sitn.storageInstance(),
            userConfigService,
            storageIndexProvider,
            after -> {
              sitn.setUserObject(after.getName());
              tree.model().nodeChanged(sitn);
            });
        final ImportStorageDialog dialog = new ImportStorageDialog(controller);
        dialog.pack();
        dialog.setLocationRelativeTo(MainTreeView.this);
        dialog.setVisible(true);
      });
      edit.setToolTipText("Edit the Storage Instance's name and connection settings.");
      return edit;
    }
  }

}
