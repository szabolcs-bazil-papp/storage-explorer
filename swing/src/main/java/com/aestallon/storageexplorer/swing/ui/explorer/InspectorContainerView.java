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

package com.aestallon.storageexplorer.swing.ui.explorer;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.core.event.TreeTouchRequest;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.swing.ui.inspector.InspectorView;
import com.aestallon.storageexplorer.swing.ui.inspector.StorageEntryInspectorViewFactory;
import com.aestallon.storageexplorer.swing.ui.misc.CloseTabButton;

@Component
public class InspectorContainerView extends JTabbedPane {

  private final ApplicationEventPublisher eventPublisher;
  private final StorageEntryInspectorViewFactory factory;

  public InspectorContainerView(ApplicationEventPublisher eventPublisher,
                                StorageEntryInspectorViewFactory factory) {
    super(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    this.eventPublisher = eventPublisher;

    setMinimumSize(new Dimension(100, 0));
    this.factory = factory;

    addChangeListener(e -> {
      InspectorView<? extends StorageEntry> selectedComponent =
          (InspectorView<? extends StorageEntry>) getSelectedComponent();
      if (selectedComponent != null) {
        eventPublisher.publishEvent(new TreeTouchRequest(
            selectedComponent.storageEntry()));
      }
    });
  }

  public void showInspectorView(final StorageEntry storageEntry) {
    switch (factory.inspectorRendering(storageEntry)) {
      case DIALOG:
        factory.focusDialog(storageEntry);
        break;
      case TAB:
        factory.getTab(storageEntry).ifPresent(tab -> setSelectedComponent(tab.asComponent()));
        break;
      case NONE:
        final var inspector = factory.createInspector(storageEntry).asComponent();
        addTab(storageEntry.toString(), inspector);
        installTabComponent(inspector);
        setSelectedComponent(inspector);
    }
  }

  public void discardInspectorViewOfStorageAt(final StorageInstance storageInstance) {
    final List<InspectorView<? extends StorageEntry>> viewsOnStorage = new ArrayList<>();
    for (int i = 0; i < getTabCount(); i++) {
      final InspectorView<? extends StorageEntry> inspectorView = inspectorViewAt(i);
      if (inspectorView.storageEntry().storageId().equals(storageInstance.id())) {
        viewsOnStorage.add(inspectorView);
      }
    }

    viewsOnStorage.forEach(it -> {
      remove(it.asComponent());
      factory.dropInspector(it);
    });
  }

  private void installTabComponent(JComponent inspectorComponent) {
    int index = indexOfComponent(inspectorComponent);

    final var tab = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    tab.setOpaque(false);
    final var label = new JLabel() {
      @Override
      public String getText() {
        int idx = InspectorContainerView.this.indexOfTabComponent(tab);
        if (idx < 0) {
          return "";
        }
        return InspectorContainerView.this.getTitleAt(idx);
      }
    };
    tab.add(label);
    tab.add(new CloseTabButton(e -> {
      int idx = InspectorContainerView.this.indexOfTabComponent(tab);
      if (idx < 0) {
        return;
      }

      final var inspector = inspectorViewAt(idx);
      if (inspector == null) {
        return;
      }

      final int alt = ActionEvent.ALT_MASK;
      if ((e.getModifiers() & alt) == alt) {
        final int tabCount = getTabCount();
        if (idx < tabCount - 1) {
          for (int i = 0; i < tabCount - 1 - idx; i++) {
            final var inspectorToClose = inspectorViewAt(idx + 1);
            discardInspector(inspectorToClose);
          }
        }
        for (int i = 0; i < idx; i++) {
          final var inspectorToClose = inspectorViewAt(0);
          discardInspector(inspectorToClose);

        }
      } else {
        discardInspector(inspector);
      }
    }));
    setTabComponentAt(index, tab);
  }

  private void discardInspector(final InspectorView<? extends StorageEntry> inspectorToClose) {
    factory.dropInspector(inspectorToClose);
    remove(inspectorToClose.asComponent());
  }

  private InspectorView<?> inspectorViewAt(final int idx) {
    return (InspectorView<? extends StorageEntry>) getComponentAt(idx);
  }

}
