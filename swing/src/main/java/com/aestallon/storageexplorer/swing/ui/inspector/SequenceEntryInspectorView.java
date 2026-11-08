package com.aestallon.storageexplorer.swing.ui.inspector;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.swing.ui.explorer.TabContainer;
import com.aestallon.storageexplorer.swing.ui.explorer.TabViewThumbnail;
import com.aestallon.storageexplorer.swing.ui.misc.AutoSizingTextArea;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.LafService;
import com.aestallon.storageexplorer.swing.ui.misc.OpenInSystemExplorerAction;
import com.aestallon.storageexplorer.swing.ui.storagetree.StorageTreeView;
import com.aestallon.storageexplorer.swing.ui.tree.TreeEntityLocator;

public class SequenceEntryInspectorView extends JPanel implements InspectorView<SequenceEntry> {

  private final transient SequenceEntry sequenceEntry;
  private final transient StorageEntryInspectorViewFactory factory;

  private JLabel labelName;
  private JTextArea textareaDescription;

  public SequenceEntryInspectorView(SequenceEntry sequenceEntry,
                                    StorageEntryInspectorViewFactory factory) {
    this.sequenceEntry = sequenceEntry;
    this.factory = factory;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(new EmptyBorder(5, 5, 5, 5));

    initToolbar();
    initMeta();
    initValue();
  }

  protected TabContainer container;

  @Override
  public void container(TabContainer container) {
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
        factory.trackingService().getUserData(storageEntry())
            .map(StorageEntryTrackingService.StorageEntryUserData::name)
            .filter(it -> !it.isBlank())
            .orElseGet(() -> StorageEntry.typeNameOf(storageEntry())),
        "<B>%s</B> (%s)".formatted(
            factory.storageInstanceProvider().get(storageId()).name(),
            storageEntry().uri().toString()),
        new TreeEntityLocator(StorageTreeView.TREE_NAME_STORAGES, storageEntry()));
  }

  private void initToolbar() {
    final var toolbar = new JToolBar(JToolBar.TOP);
    toolbar.setOrientation(SwingConstants.HORIZONTAL);
    toolbar.setBorder(new EmptyBorder(5, 0, 5, 0));
    toolbar.add(new OpenInSystemExplorerAction(
        factory.storageInstanceProvider().get(sequenceEntry.storageId()), sequenceEntry, this));
    factory.addEditMetaAction(sequenceEntry, toolbar);
    factory.addCloseAndForgetAction(this, toolbar);
    toolbar.setAlignmentX(LEFT_ALIGNMENT);
    toolbar.setMinimumSize(new Dimension(Integer.MAX_VALUE, toolbar.getPreferredSize().height));
    add(toolbar);
  }

  private void initMeta() {
    final var panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.setOpaque(false);
    labelName = new JLabel(factory.trackingService().getUserData(sequenceEntry)
        .map(StorageEntryTrackingService.StorageEntryUserData::name)
        .filter(it -> !it.isBlank())
        .map(it -> it + " - " + sequenceEntry.toString())
        .orElseGet(sequenceEntry::toString));
    labelName.setFont(LafService.font(LafService.FontToken.H3_SEMIBOLD));
    labelName.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, labelName.getPreferredSize().height));
    panel.add(labelName);
    panel.setAlignmentX(LEFT_ALIGNMENT);
    add(panel);

    final var description = factory.trackingService().getUserData(sequenceEntry)
        .map(StorageEntryTrackingService.StorageEntryUserData::description)
        .filter(it -> !it.isBlank())
        .orElse("");
    textareaDescription = new AutoSizingTextArea(description);
    factory.setDescriptionTextAreaProps(textareaDescription);
    add(textareaDescription);

    add(Box.createVerticalStrut(5));
  }

  private void initValue() {
    final var label = new JLabel("Current value:");
    label.setFont(LafService.font(LafService.FontToken.H4));
    label.setAlignmentX(LEFT_ALIGNMENT);
    add(label);
    
    final var value = new JLabel(String.valueOf(currentValue()));
    value.setFont(LafService.font(LafService.FontToken.H2_SEMIBOLD));
    value.setAlignmentX(LEFT_ALIGNMENT);
    add(value);
  }

  /** The sequence's current value, read fresh off its content (the {@code "current"} field) —
   *  replaces the removed {@code SequenceEntry#current()} (T4.1); sequences read through the
   *  same {@code StorageEngine.read}/{@code ContentHandle} path as any other entry. */
  private long currentValue() {
    final var storageInstance = factory.storageInstanceProvider().get(sequenceEntry.storageId());
    return switch (storageInstance.read(sequenceEntry)) {
      case ContentLoadResult.Ok ok -> switch (ok.content().materialize()) {
        case LoadedContent.JsonObject(var data) -> {
          try {
            yield Long.parseLong(String.valueOf(data.get("current")));
          } catch (final Exception e) {
            yield -1L;
          }
        }
        default -> -1L;
      };
      case ContentLoadResult.Err err -> -1L;
    };
  }

  @Override
  public SequenceEntry storageEntry() {
    return sequenceEntry;
  }

  @Override
  public void onUserDataChanged(StorageEntryTrackingService.StorageEntryUserData userData) {
    labelName.setText(userData.name() == null || userData.name().isBlank()
        ? sequenceEntry.toString()
        : userData.name() + " - " + sequenceEntry.toString());
    textareaDescription.setText(userData.description() == null || userData.description().isBlank()
        ? ""
        : userData.description());
  }
}
