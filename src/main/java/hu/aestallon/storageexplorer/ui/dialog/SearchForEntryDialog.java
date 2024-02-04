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

package hu.aestallon.storageexplorer.ui.dialog;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Comparator;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.springframework.context.ApplicationEventPublisher;
import com.formdev.flatlaf.ui.FlatUIUtils;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import hu.aestallon.storageexplorer.ui.controller.ViewController;

public class SearchForEntryDialog extends JFrame {

  private final StorageIndex storageIndex;
  private final ApplicationEventPublisher eventPublisher;

  public SearchForEntryDialog(StorageIndex storageIndex, ApplicationEventPublisher eventPublisher) {
    this.storageIndex = storageIndex;
    this.eventPublisher = eventPublisher;

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setTitle("Search for Storage Entry...");
    add(new SearchForEntryView());
    pack();
    addCancelAction();
  }

  private void addCancelAction() {
    InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = getRootPane().getActionMap();

    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
    am.put("cancel", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        dispose();
      }
    });
  }


  private final class SearchForEntryView extends JPanel {

    private JTextField textField;
    private JList<StorageEntry> results;

    private SearchForEntryView() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setBorder(new EmptyBorder(5, 5, 5, 5));


      initTextfield();
      initList();
    }

    private void initTextfield() {
      final var label = new JLabel("Query for:");
      label.setFont(FlatUIUtils.nonUIResource(UIManager.getFont("h4.font")));
      label.setAlignmentX(LEFT_ALIGNMENT);
      label.setBorder(new EmptyBorder(10, 0, 5, 10));
      SearchForEntryView.this.add(label);

      textField = new JTextField("", 50);
      textField.getDocument().addDocumentListener((SimpleDocumentListener) e -> {
        String text = textField.getText();
        results.setListData(storageIndex.searchForUri(text)
            .sorted(Comparator.comparing(it -> it.uri().toString()))
            .toArray(StorageEntry[]::new));
      });
      textField.setPreferredSize(new Dimension(50, 40));
      SearchForEntryView.this.add(textField);
    }

    private void initList() {
      final var label = new JLabel("Results:");
      label.setFont(FlatUIUtils.nonUIResource(UIManager.getFont("h4.font")));
      label.setAlignmentX(LEFT_ALIGNMENT);
      label.setBorder(new EmptyBorder(10, 0, 5, 10));
      SearchForEntryView.this.add(label);

      results = new JList<>();
      results.setCellRenderer(new EntryListElementRenderer());
      results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      results.addListSelectionListener(e -> {
        final int idx = e.getFirstIndex();
        if (idx < 0 || idx >= results.getModel().getSize()) {
          return;
        }

        final StorageEntry storageEntry = results.getModel().getElementAt(idx);
        if (storageEntry == null) {
          return;
        }

        SearchForEntryDialog.this.dispose();
        eventPublisher.publishEvent(new ViewController.EntryInspectionEvent(storageEntry));
      });
      final var scrollPane = new JScrollPane(results);
      scrollPane.setMinimumSize(new Dimension(50, 500));
      scrollPane.setPreferredSize(new Dimension(50, 500));
      SearchForEntryView.this.add(scrollPane);
    }
  }


  private final static class EntryListElementRenderer
      extends JLabel
      implements ListCellRenderer<StorageEntry> {

    @Override
    public Component getListCellRendererComponent(JList<? extends StorageEntry> list,
                                                  StorageEntry value, int index, boolean isSelected,
                                                  boolean cellHasFocus) {
      setText(value.uri().toString());
      setEnabled(true);
      setOpaque(false);
      setBorder(new EmptyBorder(2, 2, 2, 2));

      return this;
    }

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

}
