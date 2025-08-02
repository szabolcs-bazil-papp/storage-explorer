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

package com.aestallon.storageexplorer.swing.ui.inspector;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import static java.util.stream.Collectors.toList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.util.Uris;
import com.aestallon.storageexplorer.swing.ui.misc.AutoSizingTextArea;
import com.aestallon.storageexplorer.swing.ui.misc.EnumeratorWithUri;
import com.aestallon.storageexplorer.swing.ui.misc.LafService;
import com.aestallon.storageexplorer.swing.ui.misc.OpenInSystemExplorerAction;

public class CollectionEntryInspectorView extends JPanel implements InspectorView<StorageEntry> {

  private static final Logger log = LoggerFactory.getLogger(CollectionEntryInspectorView.class);

  private JToolBar toolBar;
  private JTable table;
  private StorageCollectionTableModel tableModel;
  private JScrollPane pane;
  private JLabel labelName;
  private JTextArea textareaDescription;

  private final transient StorageEntry storageEntry;
  private final transient StorageEntryInspectorViewFactory factory;

  public CollectionEntryInspectorView(StorageEntry storageEntry,
                                      StorageEntryInspectorViewFactory factory) {
    this.storageEntry = storageEntry;
    this.factory = factory;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(new EmptyBorder(5, 5, 5, 5));

    initToolbar();
    initMeta();
    initTable();
  }

  @Override
  public List<JTextArea> textAreas() {
    return Collections.emptyList();
  }

  private void initToolbar() {
    final var toolbar = new JToolBar(JToolBar.TOP);
    toolbar.setOrientation(SwingConstants.HORIZONTAL);
    toolbar.setBorder(new EmptyBorder(5, 0, 5, 0));
    factory.addRenderAction(storageEntry, toolbar);
    toolbar.add(new OpenInSystemExplorerAction(storageEntry, this));
    factory.addEditMetaAction(storageEntry, toolbar);
    factory.addModifyAction(storageEntry, singleVersionSupplier(), -1, null, toolbar);
    toolbar.add(Box.createHorizontalGlue());
    add(toolbar);
  }

  private Supplier<ObjectEntryLoadResult.SingleVersion> singleVersionSupplier() {
    return switch (storageEntry) {
      case ListEntry l -> l::asSingleVersion;
      case MapEntry m -> m::asSingleVersion;
      default -> null;
    };
  }

  private void initMeta() {
    final var panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.setOpaque(false);
    labelName = new JLabel(factory.trackingService().getUserData(storageEntry)
        .map(StorageEntryTrackingService.StorageEntryUserData::name)
        .filter(it -> !it.isBlank())
        .map(it -> it + " - " + storageEntry.toString())
        .orElseGet(storageEntry::toString));
    labelName.setFont(LafService.wrap(UIManager.getFont("h3.font")));
    labelName.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.setMaximumSize(new Dimension(
        Integer.MAX_VALUE,
        labelName.getPreferredSize().height));
    panel.add(labelName);
    add(panel);

    final var description = factory.trackingService().getUserData(storageEntry)
        .map(StorageEntryTrackingService.StorageEntryUserData::description)
        .filter(it -> !it.isBlank())
        .orElse("");
    textareaDescription = new AutoSizingTextArea(description);
    factory.setDescriptionTextAreaProps(textareaDescription);
    textareaDescription.setMaximumSize(
        new Dimension(Integer.MAX_VALUE, textareaDescription.getPreferredSize().height));
    add(textareaDescription);

    add(Box.createVerticalStrut(5));
  }

  private void initTable() {
    tableModel = new StorageCollectionTableModel(storageEntry);
    table = new JTable(tableModel);
    table.setAutoCreateRowSorter(true);
    table.addMouseListener(new MouseAdapter() {

      @Override
      public void mouseClicked(MouseEvent e) {
        final Point eventLocation = e.getPoint();
        final int row = table.rowAtPoint(eventLocation);
        if (row < 0) {
          return;
        }

        final URI uri = tableModel.uriAt(row);
        factory.jumpToUri(storageEntry.storageId(), Uris.latest(uri));
      }

    });
    pane = new JScrollPane(table);
    table.setFillsViewportHeight(true);
    add(pane);
  }

  @Override
  public StorageEntry storageEntry() {
    return storageEntry;
  }

  @Override
  public void onUserDataChanged(StorageEntryTrackingService.StorageEntryUserData userData) {
    labelName.setText(userData.name() == null || userData.name().isBlank()
        ? storageEntry.toString()
        : userData.name() + " - " + storageEntry.toString());
    textareaDescription.setText(userData.description() == null || userData.description().isBlank()
        ? ""
        : userData.description());
  }

  private static final class StorageCollectionTableModel
      extends AbstractTableModel
      implements EnumeratorWithUri {

    private final StorageEntry storageEntry;
    private final List<UriProperty> uris;

    private StorageCollectionTableModel(StorageEntry storageEntry) {
      this.storageEntry = storageEntry;
      uris = storageEntry.uriProperties().stream()
          .sorted()
          .collect(toList());
    }

    @Override
    public String getColumnName(int column) {
      return column == 0
          ? (storageEntry instanceof ListEntry) ? "index" : "key"
          : column == 1 ? "URI" : "((( UNKNOWN )))";
    }

    @Override
    public int getRowCount() {
      return uris.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return columnIndex == 0
          ? switch (uris.get(rowIndex).segments[0]) {
        case UriProperty.Segment.Key(String s) -> s;
        case UriProperty.Segment.Idx(int value) -> value;
      }
          : uris.get(rowIndex).uri();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == 0
          ? (storageEntry instanceof ListEntry) ? Integer.class : String.class
          : columnIndex == 1 ? URI.class : null;
    }

    @Override
    public URI uriAt(int idx) {
      return uris.get(idx).uri();
    }
  }

}
