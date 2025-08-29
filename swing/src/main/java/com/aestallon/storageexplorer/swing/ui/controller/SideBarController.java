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
import com.aestallon.storageexplorer.swing.ui.commander.CommanderView;
import com.aestallon.storageexplorer.swing.ui.tree.TreeEntityLocator;
import com.aestallon.storageexplorer.swing.ui.tree.TreeView;

@Service
public class SideBarController {

  public sealed interface TreeShowEvent {

    record Some(TreeView<?, ?> treeView) implements TreeShowEvent {}


    record None() implements TreeShowEvent {}

  }


  public sealed interface CommanderShowEvent {

    record Some(CommanderView commanderView) implements CommanderShowEvent {}


    record None() implements CommanderShowEvent {}

  }


  private record TreeViewContext(TreeView<?, ?> treeView, JToggleButton toggleButton) {}


  private record CommanderViewContext(CommanderView commanderView, JToggleButton toggleButton) {}


  private final ApplicationEventPublisher eventPublisher;
  private final Map<String, TreeViewContext> treeContextByName;
  private final Map<String, CommanderViewContext> commanderContextByName;
  
  private volatile boolean treeMayShow = true;

  public SideBarController(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
    this.treeContextByName = new LinkedHashMap<>();
    this.commanderContextByName = new LinkedHashMap<>();
  }

  public void registerTreeView(final TreeView<?, ?> treeView) {
    final String name = treeView.name();
    final JToggleButton toggleButton = new JToggleButton(treeView.icon());
    toggleButton.setToolTipText(treeView.tooltip());
    toggleButton.setFocusPainted(false);
    toggleButton.setSelected(false);
    toggleButton.addActionListener(treeToggleListener(name, toggleButton));
    treeContextByName.put(name, new TreeViewContext(treeView, toggleButton));
  }

  public List<JToggleButton> getTreeToggles() {
    return treeContextByName.values().stream()
        .map(TreeViewContext::toggleButton)
        .toList();
  }

  private ActionListener treeToggleListener(final String id, final JToggleButton toggleButton) {
    return e -> {
      final boolean selected = toggleButton.isSelected();
      treeMayShow = selected;
      if (selected) {
        showTreeViewInternal(treeContextByName.get(id).treeView);
      } else {
        eventPublisher.publishEvent(new TreeShowEvent.None());
      }
    };
  }

  public void registerCommanderView(final CommanderView commanderView) {
    final String name = commanderView.name();
    final JToggleButton toggleButton = new JToggleButton(commanderView.icon());
    toggleButton.setToolTipText(commanderView.tooltip());
    toggleButton.setFocusPainted(false);
    toggleButton.setSelected(false);
    toggleButton.addActionListener(commanderToggleListener(name, toggleButton));
    commanderContextByName.put(name, new CommanderViewContext(commanderView, toggleButton));
  }

  public List<JToggleButton> getCommanderToggles() {
    return commanderContextByName.values().stream()
        .map(CommanderViewContext::toggleButton)
        .toList();
  }


  private ActionListener commanderToggleListener(final String id,
                                                 final JToggleButton toggleButton) {
    return e -> {
      final boolean selected = toggleButton.isSelected();
      if (selected) {
        showCommanderViewInternal(commanderContextByName.get(id).commanderView);
      } else {
        eventPublisher.publishEvent(new CommanderShowEvent.None());
      }
    };
  }

  public void showTreeView(final TreeView<?, ?> treeView) {
    if (!treeMayShow) {
      return;
    }
    
    treeContextByName.get(treeView.name()).toggleButton().setSelected(true);
    showTreeViewInternal(treeView);
  }

  private void showTreeViewInternal(final TreeView<?, ?> treeView) {
    final String name = treeView.name();
    treeContextByName.values().stream()
        .filter(ctx -> !name.equals(ctx.treeView.name()))
        .forEach(ctx -> ctx.toggleButton.setSelected(false));
    eventPublisher.publishEvent(new TreeShowEvent.Some(treeView));
  }

  public void showCommanderView(final CommanderView commanderView) {
    commanderContextByName.get(commanderView.name()).toggleButton().setSelected(true);
    showCommanderViewInternal(commanderView);
  }

  private void showCommanderViewInternal(final CommanderView commanderView) {
    final String name = commanderView.name();
    commanderContextByName.values().stream()
        .filter(ctx -> !name.equals(ctx.commanderView.name()))
        .forEach(ctx -> ctx.toggleButton.setSelected(false));
    eventPublisher.publishEvent(new CommanderShowEvent.Some(commanderView));
  }

  public Optional<TreeView<?, ?>> treeView(final String name) {
    return Optional.ofNullable(treeContextByName.get(name)).map(TreeViewContext::treeView);
  }

  public Optional<CommanderView> commanderView(final String name) {
    return Optional
        .ofNullable(commanderContextByName.get(name))
        .map(CommanderViewContext::commanderView);
  }
  
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void select(TreeEntityLocator locator) {
    Optional.ofNullable(treeContextByName.get(locator.treeName()))
        .map(TreeViewContext::treeView)
        .map(it -> (TreeView) it)
        .ifPresent(tree -> tree.selectNode(locator.entityLocator()));
  }

}
