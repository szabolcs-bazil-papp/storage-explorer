/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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
import java.awt.event.ActionEvent;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.model.entry.ObjectReferenceByIdEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.util.Uris;
import com.aestallon.storageexplorer.swing.ui.explorer.TabContainer;
import com.aestallon.storageexplorer.swing.ui.explorer.TabViewThumbnail;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.LafService;
import com.aestallon.storageexplorer.swing.ui.storagetree.StorageTreeView;
import com.aestallon.storageexplorer.swing.ui.tree.TreeEntityLocator;

/**
 * A deliberately minimal, read-only {@link InspectorView} for {@link ObjectReferenceByIdEntry}
 * entries: these are pure pointer objects, so there is nothing to render, diff or modify - just
 * the logical id they represent, the URI they point to, and a single action to jump there.
 */
public class ObjectReferenceByIdInspectorView extends JPanel
    implements InspectorView<ObjectReferenceByIdEntry> {

  private final transient ObjectReferenceByIdEntry objectReferenceByIdEntry;
  private final transient StorageEntryInspectorViewFactory factory;

  protected TabContainer container;

  public ObjectReferenceByIdInspectorView(final ObjectReferenceByIdEntry objectReferenceByIdEntry,
                                          final StorageEntryInspectorViewFactory factory) {
    this.objectReferenceByIdEntry = objectReferenceByIdEntry;
    this.factory = factory;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(new EmptyBorder(5, 5, 5, 5));

    final var result = objectReferenceByIdEntry.tryLoad().get();
    switch (result) {
      case ObjectEntryLoadResult.Err err -> initUi(null, err.msg());
      case ObjectEntryLoadResult.SingleVersion sv -> initUi(sv, null);
      case ObjectEntryLoadResult.MultiVersion mv -> initUi(mv.head(), null);
    }
  }

  private void initUi(final ObjectEntryLoadResult.SingleVersion version, final String error) {
    final URI refObjectUri = (version == null)
        ? null
        : Uris.parse(version.objectAsMap().get("refObjectUri")).orElse(null);

    final var toolbar = new JToolBar(SwingConstants.TOP);
    toolbar.setOrientation(SwingConstants.HORIZONTAL);
    toolbar.setBorder(new EmptyBorder(5, 0, 5, 0));
    toolbar.add(jumpToReferenceAction(refObjectUri));
    factory.addCloseAndForgetAction(this, toolbar);
    toolbar.add(Box.createHorizontalGlue());
    add(toolbar);
    toolbar.setAlignmentX(LEFT_ALIGNMENT);

    final var title = new JLabel("Reference (" + objectReferenceByIdEntry.uuid() + ")");
    title.setFont(LafService.font(LafService.FontToken.H3_SEMIBOLD));
    title.setAlignmentX(LEFT_ALIGNMENT);
    add(title);

    add(Box.createVerticalStrut(10));
    add(fieldRow("Logical ID:", objectReferenceByIdEntry.uuid()));

    if (error != null) {
      add(Box.createVerticalStrut(10));
      add(fieldRow("Error:", error));
    } else {
      add(fieldRow("Points to:", refObjectUri == null ? "N/A" : refObjectUri.toString()));
    }

    add(Box.createVerticalGlue());
  }

  private Action jumpToReferenceAction(final URI refObjectUri) {
    final var action = new AbstractAction(null, IconProvider.LINK) {
      @Override
      public void actionPerformed(final ActionEvent e) {
        factory.jumpToUri(objectReferenceByIdEntry.storageId(), refObjectUri);
      }
    };
    action.putValue(
        Action.SHORT_DESCRIPTION,
        "Jump to the referenced entity");
    action.setEnabled(refObjectUri != null);
    return action;
  }

  private JComponent fieldRow(final String label, final String value) {
    final Box box = new Box(BoxLayout.X_AXIS);
    box.setAlignmentX(LEFT_ALIGNMENT);

    final var labelComponent = new JLabel(label);
    labelComponent.setFont(LafService.font(LafService.FontToken.H4));
    labelComponent.setBorder(new EmptyBorder(0, 0, 0, 5));
    box.add(labelComponent);

    final var valueComponent = new JLabel(value);
    valueComponent.setFont(LafService.font(LafService.FontToken.MEDIUM));
    box.add(valueComponent);

    box.add(Box.createHorizontalGlue());
    return box;
  }

  @Override
  public void container(final TabContainer container) {
    this.container = container;
  }

  @Override
  public TabContainer container() {
    return container;
  }

  @Override
  public List<JTextArea> textAreas() {
    return Collections.emptyList();
  }

  @Override
  public TabViewThumbnail thumbnail() {
    return new TabViewThumbnail(
        IconProvider.getIconForStorageEntry(storageEntry()),
        StorageEntry.typeNameOf(storageEntry()) + " (" + objectReferenceByIdEntry.uuid() + ")",
        "<B>%s</B> (%s)".formatted(
            factory.storageInstanceProvider().get(storageId()).name(),
            storageEntry().uri().toString()),
        new TreeEntityLocator(StorageTreeView.TREE_NAME_STORAGES, storageEntry()));
  }

  @Override
  public ObjectReferenceByIdEntry storageEntry() {
    return objectReferenceByIdEntry;
  }

  @Override
  public void onUserDataChanged(final StorageEntryTrackingService.StorageEntryUserData userData) {
    // this view surfaces no user-editable metadata (no edit-meta action on its toolbar), so there
    // is nothing here to refresh.
  }

}
