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

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.common.util.Pair;
import static com.aestallon.storageexplorer.common.util.Streams.enumerationToStream;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.swing.ui.event.BreadCrumbsChanged;

/**
 * Skeleton implementation for "static" lifecycle trees.
 *
 * @param <TREE> the type of the rendered tree
 * @param <ENTITY> the common ancestor type of the entities represented by interactive nodes
 * @param <USER_DATA_CHANGE_EVENT> event type pertaining to entity meta changes by the user
 * @param <STORAGE_NODE> type of tree nodes representing a
 *     {@link com.aestallon.storageexplorer.core.model.instance.StorageInstance}
 * @param <ENTITY_NODE> common ancestor type of interactive nodes
 * @param <T> the actual implementation class - only here for technical reasons
 *
 * @author Szabolcs Bazil Papp
 */
public abstract class AbstractTreeView
    <
        TREE extends JTree & Scrollable & Accessible,
        ENTITY,
        USER_DATA_CHANGE_EVENT,
        STORAGE_NODE extends DefaultMutableTreeNode & AbstractTreeView.StorageInstanceNode,
        ENTITY_NODE extends DefaultMutableTreeNode & AbstractTreeView.EntityNode<ENTITY>,
        /*  This parameter shall go when I upgrade to a Java version with ctor preludes: */
        T extends AbstractTreeView
            <
                TREE,
                ENTITY,
                USER_DATA_CHANGE_EVENT,
                STORAGE_NODE,
                ENTITY_NODE,
                T>>
    extends JPanel
    implements TreeView
    <
        ENTITY,
        USER_DATA_CHANGE_EVENT> {

  private static final Logger log = LoggerFactory.getLogger(AbstractTreeView.class);


  public interface StorageInstanceNode {

    StorageId storageId();

  }


  public interface EntityNode<T> {

    T entity();

  }

  private static Stream<DefaultMutableTreeNode> flatten(DefaultMutableTreeNode node) {
    return Stream.concat(
        Stream.of(node),
        enumerationToStream(node.children())
            .filter(DefaultMutableTreeNode.class::isInstance)
            .map(DefaultMutableTreeNode.class::cast)
            .flatMap(AbstractTreeView::flatten));
  }

  protected final TREE tree;
  protected final JScrollPane scrollPane;

  protected final transient Map<ENTITY, TreePath> treePathsByLeaf;
  protected final transient ApplicationEventPublisher eventPublisher;
  protected final transient StorageInstanceProvider storageInstanceProvider;
  protected final transient UserConfigService userConfigService;

  protected boolean propagate = true;

  protected AbstractTreeView(ApplicationEventPublisher eventPublisher,
                             StorageInstanceProvider storageInstanceProvider,
                             UserConfigService userConfigService,
                             Consumer<T> prelude) {
    /* ctor prelude runs first to let inheritors set their own private fields: */
    prelude.accept((T) this);

    this.eventPublisher = eventPublisher;
    this.storageInstanceProvider = storageInstanceProvider;
    this.userConfigService = userConfigService;

    setLayout(new GridLayout(1, 1));
    setMinimumSize(new Dimension(100, 500));

    tree = initTree();
    tree.addTreeSelectionListener(e -> {
      final Object terminal = e.getPath().getLastPathComponent();
      if (propagate && entityNodeType().isInstance(terminal)) {
        eventPublisher.publishEvent(terminal);
      }
    });
    scrollPane = new JScrollPane(tree);
    treePathsByLeaf = new HashMap<>();

    add(scrollPane);
  }

  protected abstract TREE initTree();

  @Override
  public void selectNode(ENTITY entity) {
    Optional
        .ofNullable(treePathsByLeaf.get(entity))
        .ifPresent(path -> {
          selectEntryInternal(path);
          eventPublisher.publishEvent(new BreadCrumbsChanged(path));
        });
  }

  protected void selectEntryInternal(TreePath path) {
    tree.setSelectionPath(path);
    tree.scrollPathToVisible(path);
  }

  @Override
  public void selectNodeSoft(ENTITY entity) {
    propagate = false;  // FIXME: This is a freaking hack!
    selectNode(entity);
    propagate = true;
  }

  public final void selectNodeSoft(DefaultMutableTreeNode node) {
    final var path = new TreePath(node.getPath());
    selectEntryInternal(path);
  }

  @Override
  public void onUserDataChanged(USER_DATA_CHANGE_EVENT userDataChangeEvent) {
    SwingUtilities.invokeLater(tree::repaint);
  }

  @SuppressWarnings("unchecked")
  protected final void memoizeTreePathsOfStorage(STORAGE_NODE node) {
    final var newTreePaths = Stream.of(node)
        .flatMap(AbstractTreeView::flatten)
        .filter(entityNodeType()::isInstance)
        .map(it -> Pair.of(
            ((ENTITY_NODE) it).entity(),
            new TreePath(it.getPath())))
        .collect(Pair.toMap());
    treePathsByLeaf.putAll(newTreePaths);
  }

  protected abstract Class<? extends ENTITY_NODE> entityNodeType();

  protected final void memoizeTreePathsOf(ENTITY_NODE node) {
    final var oldPath = treePathsByLeaf.put(
        node.entity(),
        new TreePath(node.getPath()));
    if (oldPath != null) {
      log.warn("Overwriting tree path for entry {}: {}", node.entity(), oldPath);
    }
  }

}
