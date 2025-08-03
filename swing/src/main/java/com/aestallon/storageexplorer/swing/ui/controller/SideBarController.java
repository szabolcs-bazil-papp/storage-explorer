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

import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.swing.ui.tree.TreeView;

@Service
public class SideBarController {

  public sealed interface TreeShowEvent {

    record Some(TreeView<?, ?> treeView) implements TreeShowEvent {}


    record None() implements TreeShowEvent {}

  }


  private record TreeViewContext(TreeView<?, ?> treeView, JToggleButton toggleButton) {}


  private final ApplicationEventPublisher eventPublisher;
  private final Map<String, TreeViewContext> contextByName;

  public SideBarController(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
    this.contextByName = new LinkedHashMap<>();
  }

  public void registerTreeView(final TreeView<?, ?> treeView) {
    final String name = treeView.name();
    final JToggleButton toggleButton = new JToggleButton(treeView.icon());
    toggleButton.setToolTipText(treeView.tooltip());
    toggleButton.setFocusPainted(false);
    toggleButton.setSelected(false);
    toggleButton.addActionListener(toggleListener(name, toggleButton));
    contextByName.put(name, new TreeViewContext(treeView, toggleButton));
  }

  public List<JToggleButton> getTreeToggles() {
    return contextByName.values().stream()
        .map(TreeViewContext::toggleButton)
        .toList();
  }

  private ActionListener toggleListener(final String id, final JToggleButton toggleButton) {
    return e -> {
      final boolean selected = toggleButton.isSelected();
      if (selected) {
        showTreeViewInternal(contextByName.get(id).treeView);
      } else {
        eventPublisher.publishEvent(new TreeShowEvent.None());
      }
    };
  }

  public void showTreeView(final TreeView<?, ?> treeView) {
    contextByName.get(treeView.name()).toggleButton().setSelected(true);
    showTreeViewInternal(treeView);
  }

  private void showTreeViewInternal(final TreeView<?, ?> treeView) {
    final String name = treeView.name();
    contextByName.values().stream()
        .filter(ctx -> !name.equals(ctx.treeView.name()))
        .forEach(ctx -> ctx.toggleButton.setSelected(false));
    eventPublisher.publishEvent(new TreeShowEvent.Some(treeView));
  }

  public Optional<TreeView<?, ?>> treeView(final String name) {
    return Optional.ofNullable(contextByName.get(name)).map(TreeViewContext::treeView);
  }

}
