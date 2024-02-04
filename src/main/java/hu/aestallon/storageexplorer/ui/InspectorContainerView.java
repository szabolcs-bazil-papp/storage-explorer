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

package hu.aestallon.storageexplorer.ui;

import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.ui.controller.ViewController;
import hu.aestallon.storageexplorer.ui.inspector.InspectorView;
import hu.aestallon.storageexplorer.ui.inspector.StorageEntryInspectorViewFactory;

@Component
public class InspectorContainerView extends JTabbedPane {

  private final ApplicationEventPublisher eventPublisher;
  private final StorageEntryInspectorViewFactory factory;

  public InspectorContainerView(ApplicationEventPublisher eventPublisher,
                                StorageEntryInspectorViewFactory factory) {
    super(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    this.eventPublisher = eventPublisher;

    this.factory = factory;

    addChangeListener(e -> {
      final var storageEntry = ((InspectorView<? extends StorageEntry>) getSelectedComponent())
          .storageEntry();
      eventPublisher.publishEvent(new ViewController.TreeTouchRequest(storageEntry));
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
        final var inspector = factory.createInspector(storageEntry);
        addTab(storageEntry.toString(), inspector.asComponent());
        setSelectedComponent(inspector.asComponent());
    }
  }

}
