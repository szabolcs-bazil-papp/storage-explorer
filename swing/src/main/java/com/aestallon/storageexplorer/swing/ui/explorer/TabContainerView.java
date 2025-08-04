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

package com.aestallon.storageexplorer.swing.ui.explorer;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.userconfig.event.StorageEntryUserDataChanged;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.event.TreeTouchRequest;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.swing.ui.arcscript.ArcScriptController;
import com.aestallon.storageexplorer.swing.ui.arcscript.tree.ArcScriptSelectorTree;
import com.aestallon.storageexplorer.swing.ui.arcscript.editor.ArcScriptView;
import com.aestallon.storageexplorer.swing.ui.controller.ViewController;
import com.aestallon.storageexplorer.swing.ui.event.ArcScriptViewRenamed;
import com.aestallon.storageexplorer.swing.ui.inspector.InspectorView;
import com.aestallon.storageexplorer.swing.ui.inspector.StorageEntryInspectorViewFactory;
import com.aestallon.storageexplorer.swing.ui.misc.CloseTabButton;

@Component
public class TabContainerView extends JTabbedPane {

  private static final Logger log = LoggerFactory.getLogger(TabContainerView.class);
  private final transient ApplicationEventPublisher eventPublisher;
  private final transient StorageEntryInspectorViewFactory factory;
  private final transient ArcScriptController arcScriptController;

  public TabContainerView(ApplicationEventPublisher eventPublisher,
                          StorageEntryInspectorViewFactory factory,
                          ArcScriptController arcScriptController) {
    super(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    this.eventPublisher = eventPublisher;
    this.arcScriptController = arcScriptController;

    setMinimumSize(new Dimension(100, 0));
    this.factory = factory;

    addChangeListener(e -> {
      final TabView selectedComponent = (TabView) getSelectedComponent();
      switch (selectedComponent) {
        case InspectorView<?> inspector -> eventPublisher.publishEvent(
            new TreeTouchRequest(inspector.storageEntry()));
        case ArcScriptView as -> eventPublisher.publishEvent(
            new ViewController.ArcScriptTreeTouchRequest(as.storedArcScript()));
        default -> log.warn("Unknown selected component: [ {} ]", selectedComponent);
      }
    });

    for (int i = 0; i < 8; i++) {
      registerKeyboardAction(
          tabSelectAction(i),
          KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.CTRL_DOWN_MASK),
          JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    registerKeyboardAction(
        tabSelectAction(-1),
        KeyStroke.getKeyStroke(KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK),
        JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private ActionListener tabSelectAction(int idx) {
    return e -> {
      int tabCount = getTabCount();
      if (idx < 0) {
        if (tabCount > 0) {
          setSelectedComponent(getComponentAt(tabCount - 1));
        } else {
          log.warn("No tabs are available!");
        }
        return;
      }

      if (idx >= tabCount) {
        log.error("Invalid tab index: [ {} / {} ]", idx, tabCount);
        return;
      }

      setSelectedComponent(getComponentAt(idx));
    };
  }

  public void showInspectorView(final StorageEntry storageEntry) {
    switch (factory.inspectorRendering(storageEntry)) {
      case DIALOG -> factory.focusDialog(storageEntry);
      case TAB -> factory
          .getTab(storageEntry)
          .ifPresent(tab -> setSelectedComponent(tab.asComponent()));
      case NONE -> {
        final var inspector = factory.createInspector(storageEntry).asComponent();
        final var title = factory.trackingService().getUserData(storageEntry)
            .map(StorageEntryTrackingService.StorageEntryUserData::name)
            .filter(it -> !it.isBlank())
            .orElseGet(storageEntry::toString);
        addTab(title, inspector);
        installTabComponent(inspector);
        setSelectedComponent(inspector);

        inspector.registerKeyboardAction(
            e -> discardTabView((InspectorView<? extends StorageEntry>) inspector),
            KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
      }
    }
  }

  public void showArcScriptView(final ArcScriptSelectorTree.ArcScriptNodeLocator locator) {
    final var view = arcScriptController.loadScript(locator.storageId(), locator.path());
    if (view != null) {
      String title = view.storedArcScript().title();
      addTab(title, view);
      installTabComponent(view.asComponent());
      setSelectedComponent(view.asComponent());
      view.asComponent().registerKeyboardAction(
          e -> discardTabView(view),
          KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
          JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
  }

  public void discardInspectorViewOfStorageAt(final StorageInstance storageInstance) {
    final List<TabView> viewsOnStorage = new ArrayList<>();
    for (int i = 0; i < getTabCount(); i++) {
      final TabView tabView = tabViewAt(i);
      if (tabView.storageId().equals(storageInstance.id())) {
        viewsOnStorage.add(tabView);
      }
    }

    viewsOnStorage.forEach(this::discardTabView);
  }

  @EventListener
  void onArcScriptViewRenamed(final ArcScriptViewRenamed e) {
    final int idx = indexOfComponent(e.arcScriptView());
    if (idx < 0) {
      return;
    }

    final var tab = (TabComponent) getTabComponentAt(idx);
    tab.label.setText(e.name());
  }

  @EventListener
  void onStorageEntryUserDataChanged(final StorageEntryUserDataChanged event) {
    SwingUtilities.invokeLater(() -> factory.getTab(event.storageEntry()).ifPresent(view -> {
      view.onUserDataChanged(event.data());
      final int idx = indexOfComponent(view.asComponent());
      if (idx >= 0) {
        final var tab = (TabComponent) getTabComponentAt(idx);
        final var name = event.data().name();
        if (name != null && !name.isBlank()) {
          tab.label.setText(name);
        } else {
          tab.label.setText(event.storageEntry().toString());
        }
      }
    }));
  }

  private final class TabComponent extends JPanel {

    private final JLabel label;

    private TabComponent(final String title) {
      super(new FlowLayout(FlowLayout.LEFT, 0, 0));
      setOpaque(false);
      label = new JLabel(title);
      add(label);
      add(new CloseTabButton(e -> {
        int idx = TabContainerView.this.indexOfTabComponent(TabComponent.this);
        if (idx < 0) {
          return;
        }

        final var tabView = tabViewAt(idx);
        if (tabView == null) {
          return;
        }

        final int alt = ActionEvent.ALT_MASK;
        if ((e.getModifiers() & alt) == alt) {
          final int tabCount = getTabCount();
          if (idx < tabCount - 1) {
            for (int i = 0; i < tabCount - 1 - idx; i++) {
              discardTabView(tabViewAt(idx + 1));
            }
          }
          for (int i = 0; i < idx; i++) {
            discardTabView(tabViewAt(0));

          }
        } else {
          discardTabView(tabView);
        }
      }));
    }

  }

  private void installTabComponent(JComponent inspectorComponent) {
    final int index = indexOfComponent(inspectorComponent);
    final var title = getTitleAt(index);
    final var tab = new TabComponent(title);
    setTabComponentAt(index, tab);
  }

  private void discardTabView(final TabView tabViewToClose) {
    remove(tabViewToClose.asComponent());
    switch (tabViewToClose) {
      case InspectorView<?> inspector -> factory.dropInspector(inspector);
      case ArcScriptView arcScriptView -> arcScriptController.drop(arcScriptView);
      default -> log.warn("Unknown tab view to close: [ {} ]", tabViewToClose);
    }
  }

  private TabView tabViewAt(final int idx) {
    return (TabView) getComponentAt(idx);
  }

}
