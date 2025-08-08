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

package com.aestallon.storageexplorer.swing.ui.dialog.findtab;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import com.aestallon.storageexplorer.swing.ui.explorer.TabViewThumbnail;

public class FindTabDialog extends JDialog {

  private final transient FindTabController controller;
  private final transient TabViewThumbnail[] total;

  private final JTextField query;
  private final JList<TabViewThumbnail> list;

  public FindTabDialog(FindTabController controller, JFrame parent) {
    super(parent, true);
    this.controller = controller;
    this.total = controller.getThumbnails();

    setUndecorated(true);
    final JPanel contentPane = new JPanel();
    setContentPane(contentPane);
    contentPane.setLayout(new BorderLayout());
    query = new JTextField("", 50);
    contentPane.add(query, BorderLayout.NORTH);

    list = new JList<>();
    list.setCellRenderer(new ThumbnailRenderer());
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    final var selectionModel = new DefaultListSelectionModel();
    selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setSelectionModel(selectionModel);
    list.setSelectionBackground(new Color(111, 124, 207));
    list.setListData(total);
    final var scrollPane = new JScrollPane(list);
    contentPane.add(scrollPane, BorderLayout.CENTER);
    query.getDocument().addDocumentListener((SimpleDocumentListener) e -> {
      String text = query.getText();
      list.setSelectedIndex(-1);
      list.setListData(controller.filter(total, text));
    });

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    contentPane.registerKeyboardAction(
        e -> dispose(),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    setupKeyBindings();
    pack();
    setLocationRelativeTo(parent);
  }

  private void setupKeyBindings() {
    final Action transferToListAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (list.getModel().getSize() > 0) {
          list.requestFocusInWindow();
          list.setSelectedIndex(0);
          list.ensureIndexIsVisible(0);
        }
      }
    };

    final Action enterAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TabViewThumbnail selectedValue = null;
        int selectedIndex = list.getSelectedIndex();

        if (selectedIndex != -1) {
          selectedValue = list.getSelectedValue();
        } else if (list.getModel().getSize() > 0) {
          selectedValue = list.getModel().getElementAt(0);
        }

        if (selectedValue != null) {
          processSelection(selectedValue);
        }
      }
    };

    final KeyStroke downArrow = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
    query
        .getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(downArrow, "transferToList");
    query.getActionMap().put("transferToList", transferToListAction);

    final KeyStroke upArrow = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);
    list
        .getInputMap(JComponent.WHEN_FOCUSED)
        .put(upArrow, "transferToTextField");
    list.getActionMap().put("transferToTextField", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int selectedIndex = list.getSelectedIndex();
        if (selectedIndex <= 0) { // First item selected or no selection
          query.requestFocusInWindow();
        } else {
          // Delegate to default UP action (move selection up)
          list.getActionMap().get("selectPreviousRow").actionPerformed(e);
        }
      }
    });

    final KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    final String enterActionKey = "enterAction";
    query.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enterKey, enterActionKey);
    query.getActionMap().put(enterActionKey, enterAction);
    list.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enterKey, enterActionKey);
    list.getActionMap().put(enterActionKey, enterAction);
  }

  private void processSelection(TabViewThumbnail selectedValue) {
    dispose();
    controller.show(selectedValue);
  }


  @FunctionalInterface
  public interface SimpleDocumentListener extends DocumentListener {
    void update(DocumentEvent e);

    @Override
    default void insertUpdate(DocumentEvent e) {
      update(e);
    }

    @Override
    default void removeUpdate(DocumentEvent e) {
      update(e);
    }

    @Override
    default void changedUpdate(DocumentEvent e) {
      update(e);
    }
  }


  private static final class ThumbnailRenderer
      extends JLabel
      implements ListCellRenderer<TabViewThumbnail> {

    @Override
    public Component getListCellRendererComponent(JList<? extends TabViewThumbnail> list,
                                                  TabViewThumbnail value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
      setIcon(value.icon());
      setText("<HTML><B>%s</B> - <I>%s</I></HTML>".formatted(value.title(), value.description()));
      setOpaque(true);
      setEnabled(true);
      setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
      return this;
    }
  }

}
