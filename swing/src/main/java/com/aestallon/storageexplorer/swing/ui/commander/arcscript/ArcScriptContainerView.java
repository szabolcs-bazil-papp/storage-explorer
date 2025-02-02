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

package com.aestallon.storageexplorer.swing.ui.commander.arcscript;

import java.awt.*;
import javax.swing.*;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.userconfig.service.ArcScriptFileService;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;

@Component
public class ArcScriptContainerView extends JTabbedPane {

  private final ArcScriptController controller;
  final NewScriptView newScriptView;

  public ArcScriptContainerView(ArcScriptController controller) {
    super(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);

    this.controller = controller;
    controller.containerView(this);

    newScriptView = addNewScriptView();
  }

  private NewScriptView addNewScriptView() {
    final var content = new NewScriptViewContent(this);
    final var view = new NewScriptView(content);
    addTab(null, IconProvider.PLUS, view);
    return view;
  }


  static final class NewScriptViewContent extends JPanel {

    JLabel emptyMessage;

    JPanel treeContainer;
    ArcScriptSelectorTree tree;

    private final ArcScriptContainerView containerView;

    private NewScriptViewContent(ArcScriptContainerView containerView) {
      this.containerView = containerView;

      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

      tree = ArcScriptSelectorTree.create();

      final var learnMore = new LearnMoreView();
      learnMore.setAlignmentX(LEFT_ALIGNMENT);
      add(learnMore);


      final var label = new JLabel("Select target Storage:");
      label.putClientProperty("FlatLaf.styleClass", "h2");
      label.setAlignmentX(LEFT_ALIGNMENT);
      add(label);

      addEmptyMessage();
    }

    void addEmptyMessage() {
      if (emptyMessage != null) {
        return;
      }

      if (treeContainer != null) {
        remove(treeContainer);
      }

      emptyMessage = new JLabel("Import at least one Storage to get started!");
      emptyMessage.putClientProperty("FlatLaf.styleClass", "h2");
      emptyMessage.setForeground(Color.RED);
      add(emptyMessage);
    }

    void addTree() {
      if (treeContainer != null) {
        return;
      }

      if (emptyMessage != null) {
        remove(emptyMessage);
        emptyMessage = null;
      }

      /*
       * +--TreeContainer-----------------------------------------------+
       * |                                                              |
       * |  +--TreePane----------------------------------------------+  |
       * |  |                                                        |  |
       * |  |  +--Tree--------------------------------------------+  |  |
       * |  |  |**************************************************|  |  |
       * |  |  |**************************************************|  |  |
       * |  |  |**************************************************|  |  |
       * |  |  |**************************************************|  |  |
       * |  |  +--------------------------------------------------+  |  |
       * |  |                                                        |  |
       * |  +--------------------------------------------------------+  |
       * |                                                              |
       * |  +--TreeControlContainer----------------------------------+  |
       * |  |<-- Glue ~~~~~~~~~~~~~~~~~~~~~~~~~~-->[ LOAD ][ CREATE ]|  |
       * |  +--------------------------------------------------------+  |
       * |                                                              |
       * +--------------------------------------------------------------+
       */
      treeContainer = new JPanel();
      treeContainer.setLayout(new BoxLayout(treeContainer, BoxLayout.Y_AXIS));

      tree.onSelectionChanged(containerView.controller.treeListener());
      final var treePane = new JScrollPane(tree);
      treePane.setAlignmentX(LEFT_ALIGNMENT);
      treeContainer.add(treePane);

      final var treeControlContainer = new JPanel();
      treeControlContainer.setAlignmentX(LEFT_ALIGNMENT);
      treeControlContainer.setLayout(new BoxLayout(treeControlContainer, BoxLayout.X_AXIS));
      treeControlContainer.add(Box.createHorizontalGlue());

      final var loadBtn = new JButton("Load");
      loadBtn.setEnabled(false);
      final var createBtn = new JButton("Create");
      createBtn.setEnabled(false);
      treeControlContainer.add(loadBtn);
      treeControlContainer.add(createBtn);
      containerView.controller.setControlButtons(loadBtn, createBtn);

      treeContainer.add(treeControlContainer);
      add(treeContainer);
    }



  }



  static final class NewScriptView extends JScrollPane {

    final NewScriptViewContent content;

    private NewScriptView(NewScriptViewContent content) {
      super(content, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
      this.content = content;
    }

  }
}
