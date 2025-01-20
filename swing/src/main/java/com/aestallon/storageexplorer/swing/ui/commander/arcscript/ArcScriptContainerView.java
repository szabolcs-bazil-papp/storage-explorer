package com.aestallon.storageexplorer.swing.ui.commander.arcscript;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.core.event.StorageImportEvent;
import com.aestallon.storageexplorer.core.event.StorageIndexDiscardedEvent;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;

@Component
public class ArcScriptContainerView extends JTabbedPane {

  private final StorageInstanceProvider storageInstanceProvider;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final UserConfigService userConfigService;
  private final MonospaceFontProvider monospaceFontProvider;

  private final NewScriptView newScriptView;
  private final List<ArcScriptView> arcScriptViews = new ArrayList<>();

  public ArcScriptContainerView(StorageInstanceProvider storageInstanceProvider,
                                ApplicationEventPublisher applicationEventPublisher,
                                UserConfigService userConfigService,
                                MonospaceFontProvider monospaceFontProvider) {
    super(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);

    this.storageInstanceProvider = storageInstanceProvider;
    this.applicationEventPublisher = applicationEventPublisher;
    this.userConfigService = userConfigService;
    this.monospaceFontProvider = monospaceFontProvider;

    newScriptView = addNewScriptView();
  }

  private NewScriptView addNewScriptView() {
    final var content = new NewScriptViewContent(this);
    final var view = new NewScriptView(content);
    addTab("+", view);
    return view;
  }

  private void newScript(final StorageInstance storageInstance) {

    final var view = new ArcScriptView(
        applicationEventPublisher,
        userConfigService,
        monospaceFontProvider,
        storageInstance);
    arcScriptViews.add(view);
    insertTab(
        "(%s) New Script-%02d".formatted(storageInstance.name(), getTabCount()),
        null,
        view,
        "Hello World!",
        getTabCount() - 1);
    setSelectedIndex(getTabCount() - 2);
  }

  @EventListener
  public void onStorageImported(StorageImportEvent event) {
    SwingUtilities.invokeLater(() -> {

      newScriptView.content.removeEmptyMessage();
      newScriptView.content.addStorageBtn(event.storageInstance());

    });
  }

  @EventListener
  public void onStorageDeleted(StorageIndexDiscardedEvent event) {
    SwingUtilities.invokeLater(() -> {

      newScriptView.content.removeStorageBtn(event.storageInstance().id());
      if (newScriptView.content.storageBtns.isEmpty()) {
        newScriptView.content.addEmptyMessage();
      }

    });
  }

  @EventListener
  public void onFontSizeChanged(
      @SuppressWarnings("unused") MonospaceFontProvider.FontSizeChange fontSizeChange) {
    SwingUtilities.invokeLater(() -> {
      final Font font = monospaceFontProvider.getFont();
      arcScriptViews.forEach(it -> it.editor().setFont(font));

    });
  }

  private static final class NewScriptViewContent extends JPanel {

    private JLabel emptyMessage;
    private final List<StorageBtn> storageBtns = new ArrayList<>();

    private final ArcScriptContainerView containerView;

    private NewScriptViewContent(ArcScriptContainerView containerView) {
      this.containerView = containerView;

      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

      final var label = new JLabel("<h1>Select target Storage:</h1>");
      add(label);

      final var storageInstances = containerView.storageInstanceProvider
          .provide()
          .toList();
      if (storageInstances.isEmpty()) {
        addEmptyMessage();
      } else {
        storageInstances.forEach(this::addStorageBtn);
      }
    }

    private void addEmptyMessage() {
      if (emptyMessage != null) {
        return;
      }

      emptyMessage = new JLabel("<h2>Import at least one Storage to get started!</h2>");
      emptyMessage.setForeground(Color.RED);
      add(emptyMessage);
    }

    private void removeEmptyMessage() {
      if (emptyMessage == null) {
        return;
      }

      remove(emptyMessage);
      emptyMessage = null;
    }

    private void addStorageBtn(final StorageInstance storageInstance) {
      final var btn = new StorageBtn(storageInstance);
      btn.addActionListener(e -> containerView.newScript(storageInstance));
      add(btn);
      storageBtns.add(btn);
    }

    private void removeStorageBtn(final StorageId id) {
      StorageBtn storageBtn = null;
      for (var btn : storageBtns) {
        if (Objects.equals(btn.storageId, id)) {
          storageBtn = btn;
          break;
        }
      }
      if (storageBtn != null) {
        remove(storageBtn);
        storageBtns.remove(storageBtn);
      }
    }
  }


  private static final class StorageBtn extends JButton {

    private final StorageId storageId;

    private StorageBtn(StorageInstance storageInstance) {
      super(storageInstance.name());
      storageId = storageInstance.id();
    }
  }


  private static final class NewScriptView extends JScrollPane {

    private final NewScriptViewContent content;

    private NewScriptView(NewScriptViewContent content) {
      super(content, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
      this.content = content;
    }

  }
}
