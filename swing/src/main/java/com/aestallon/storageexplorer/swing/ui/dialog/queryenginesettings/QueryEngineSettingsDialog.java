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

package com.aestallon.storageexplorer.swing.ui.dialog.queryenginesettings;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import javax.swing.*;
import com.aestallon.storageexplorer.arcscript.engine.QueryEngineSettings;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;

public class QueryEngineSettingsDialog extends JDialog {

  private static final long DISABLED = -1L;

  private final QueryEngineSettingsController controller;

  private JComboBox<QueryEngineSettings.EngineMode> comboEngineMode;
  private JCheckBox checkEarlyTermination;
  private JCheckBox checkCollectStageTimings;
  private JSpinner spinnerSourcePrefetch;
  private JSpinner spinnerWhereConcurrency;
  private JSpinner spinnerYieldConcurrency;
  private JSpinner spinnerQueueCapacity;
  private JSpinner spinnerCacheMaxEntries;
  private JSpinner spinnerCacheExpireAfterAccessSeconds;
  private JSpinner spinnerUnboundedSortWarnThreshold;
  private JSpinner spinnerUnboundedSortHardCap;
  private JSpinner spinnerQueryTimeoutSeconds;

  private JButton buttonApply;
  private JButton buttonOK;
  private JButton buttonCancel;

  public QueryEngineSettingsDialog(QueryEngineSettingsController controller) {
    this.controller = controller;

    setTitle("Query Engine Settings");
    setPreferredSize(new Dimension(620, 680));
    setModal(true);

    final var contentPane = buildContentPane();
    setContentPane(contentPane);
    populate(controller.initialModel());
    getRootPane().setDefaultButton(buttonOK);

    buttonApply.addActionListener(e -> onApply());
    buttonOK.addActionListener(e -> onOK());
    buttonCancel.addActionListener(e -> onCancel());

    // call onCancel() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });

    // call onCancel() on ESCAPE
    contentPane.registerKeyboardAction(
        e -> onCancel(),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private JPanel buildContentPane() {
    final var contentPane = new JPanel(new BorderLayout(10, 10));
    contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    final var form = new JPanel();
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.add(executionPanel());
    form.add(Box.createVerticalStrut(8));
    form.add(concurrencyPanel());
    form.add(Box.createVerticalStrut(8));
    form.add(cachingPanel());
    form.add(Box.createVerticalStrut(8));
    form.add(sortingPanel());
    form.add(Box.createVerticalStrut(8));
    form.add(timeoutPanel());
    form.add(Box.createVerticalGlue());

    final var scrollPane = new JScrollPane(form);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);

    contentPane.add(scrollPane, BorderLayout.CENTER);
    contentPane.add(buttonBar(), BorderLayout.SOUTH);
    return contentPane;
  }

  private JPanel executionPanel() {
    final var panel = titled("Execution");
    comboEngineMode = new JComboBox<>(QueryEngineSettings.EngineMode.values());
    checkEarlyTermination = new JCheckBox("Early termination");
    checkCollectStageTimings = new JCheckBox("Collect stage timings");

    int row = 0;
    addRow(panel, row++, "Engine mode",
        "Which QueryEngine implementation executes query instructions.",
        comboEngineMode);
    addRow(panel, row++, "",
        "When a limit is present without an order clause, cancel the upstream stages as soon "
            + "as enough entries passed the where predicate.",
        checkEarlyTermination);
    addRow(panel, row, "",
        "Attach per-stage instrumentation to query results (counters are maintained either "
            + "way; this only controls attachment).",
        checkCollectStageTimings);
    return panel;
  }

  private JPanel concurrencyPanel() {
    final var panel = titled("Concurrency & Backpressure");
    spinnerSourcePrefetch = longSpinner(150L);
    spinnerWhereConcurrency = intSpinner(-1);
    spinnerYieldConcurrency = intSpinner(-1);
    spinnerQueueCapacity = intSpinner(1, 1_024);

    int row = 0;
    addRow(panel, row++, "Source prefetch",
        "How many entries a pipeline stage initially requests from its upstream; as further "
            + "demand is only signalled per completed unit of work, this also caps a stage's "
            + "effective parallelism.",
        spinnerSourcePrefetch);
    addRow(panel, row++, "Where concurrency",
        "Maximum concurrent where predicate evaluations; -1 means unbounded.",
        spinnerWhereConcurrency);
    addRow(panel, row++, "Yield concurrency",
        "Maximum concurrent projection computations (sort key discovery and show column "
            + "rendering); -1 means unbounded.",
        spinnerYieldConcurrency);
    addRow(panel, row, "Queue capacity",
        "Buffer capacity of each pipeline stage; saturated stages block their upstream "
            + "(backpressure).",
        spinnerQueueCapacity);
    return panel;
  }

  private JPanel cachingPanel() {
    final var panel = titled("Caching");
    spinnerCacheMaxEntries = longSpinner(DISABLED);
    spinnerCacheExpireAfterAccessSeconds = longSpinner(1L, 120L);

    int row = 0;
    addRow(panel, row++, "Cache max entries",
        "Maximum size of the load cache shared across the where, order and yield phases of a "
            + "single query; -1 means unbounded.",
        spinnerCacheMaxEntries);
    addRow(panel, row, "Cache expiry (seconds)",
        "Load cache entries not accessed for this many seconds become eligible for eviction; "
            + "ignored for an unbounded cache.",
        spinnerCacheExpireAfterAccessSeconds);
    return panel;
  }

  private JPanel sortingPanel() {
    final var panel = titled("Unbounded Sort Safeguards");
    spinnerUnboundedSortWarnThreshold = intSpinner(-1, 50_000);
    spinnerUnboundedSortHardCap = intSpinner(-1);

    int row = 0;
    addRow(panel, row++, "Warn threshold",
        "Log a warning when an unbounded sort (order without limit) buffers more than this "
            + "many entries; -1 disables the warning.",
        spinnerUnboundedSortWarnThreshold);
    addRow(panel, row, "Hard cap",
        "Hard upper bound on entries retained by a sort which is not already bounded by an "
            + "explicit query limit; worst-sorting entries are discarded past this point; never "
            + "overrides an explicit limit; -1 disables the cap.",
        spinnerUnboundedSortHardCap);
    return panel;
  }

  private JPanel timeoutPanel() {
    final var panel = titled("Timeouts");
    spinnerQueryTimeoutSeconds = longSpinner(DISABLED, Duration.ofHours(1).toSeconds());

    addRow(panel, 0, "Query timeout (seconds)",
        "Last-resort watchdog on a single query execution: if the pipeline fails to deliver a "
            + "terminal signal within this duration, the query fails instead of blocking its "
            + "caller forever; -1 disables the watchdog.",
        spinnerQueryTimeoutSeconds);
    return panel;
  }

  private JPanel buttonBar() {
    final var bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttonApply = new JButton("Apply");
    buttonOK = new JButton("OK");
    buttonCancel = new JButton("Cancel");
    bar.add(buttonApply);
    bar.add(buttonOK);
    bar.add(buttonCancel);
    return bar;
  }

  private void populate(final QueryEngineSettings settings) {
    comboEngineMode.setSelectedItem(settings.engineMode());
    checkEarlyTermination.setSelected(settings.earlyTerminationEnabled());
    checkCollectStageTimings.setSelected(settings.collectStageTimings());
    spinnerSourcePrefetch.setValue(settings.sourcePrefetch());
    spinnerWhereConcurrency.setValue(settings.whereConcurrency());
    spinnerYieldConcurrency.setValue(settings.yieldConcurrency());
    spinnerQueueCapacity.setValue(settings.queueCapacity());
    spinnerCacheMaxEntries.setValue(settings.cacheMaxEntries());
    spinnerCacheExpireAfterAccessSeconds.setValue(settings.cacheExpireAfterAccess().toSeconds());
    spinnerUnboundedSortWarnThreshold.setValue(settings.unboundedSortWarnThreshold());
    spinnerUnboundedSortHardCap.setValue(settings.unboundedSortHardCap());
    spinnerQueryTimeoutSeconds.setValue(toSeconds(settings.queryTimeout()));
  }

  private QueryEngineSettings buildSettings() {
    return QueryEngineSettings.builder()
        .engineMode((QueryEngineSettings.EngineMode) comboEngineMode.getSelectedItem())
        .earlyTerminationEnabled(checkEarlyTermination.isSelected())
        .sourcePrefetch(longValue(spinnerSourcePrefetch))
        .whereConcurrency(intValue(spinnerWhereConcurrency))
        .yieldConcurrency(intValue(spinnerYieldConcurrency))
        .queueCapacity(intValue(spinnerQueueCapacity))
        .cacheMaxEntries(longValue(spinnerCacheMaxEntries))
        .cacheExpireAfterAccess(Duration.ofSeconds(longValue(spinnerCacheExpireAfterAccessSeconds)))
        .unboundedSortWarnThreshold(intValue(spinnerUnboundedSortWarnThreshold))
        .unboundedSortHardCap(intValue(spinnerUnboundedSortHardCap))
        .collectStageTimings(checkCollectStageTimings.isSelected())
        .queryTimeout(toQueryTimeout(longValue(spinnerQueryTimeoutSeconds)))
        .build();
  }

  private boolean onApply() {
    final QueryEngineSettings settings;
    try {
      settings = buildSettings();
    } catch (IllegalArgumentException e) {
      JOptionPane.showMessageDialog(
          this, e.getMessage(), "Invalid settings", JOptionPane.ERROR_MESSAGE, IconProvider.ERROR);
      return false;
    }

    controller.finish(settings);
    return true;
  }

  private void onOK() {
    if (onApply()) {
      dispose();
    }
  }

  private void onCancel() {
    dispose();
  }

  private static long toSeconds(final Duration duration) {
    return duration == null ? DISABLED : duration.toSeconds();
  }

  private static Duration toQueryTimeout(final long seconds) {
    return seconds == DISABLED ? null : Duration.ofSeconds(seconds);
  }

  private static long longValue(final JSpinner spinner) {
    return ((Number) spinner.getValue()).longValue();
  }

  private static int intValue(final JSpinner spinner) {
    return ((Number) spinner.getValue()).intValue();
  }

  private static JSpinner longSpinner(final long initial) {
    return new JSpinner(new SpinnerNumberModel(Long.valueOf(initial), null, null, Long.valueOf(1)));
  }

  private static JSpinner longSpinner(final long min, final long initial) {
    return new JSpinner(
        new SpinnerNumberModel(Long.valueOf(initial), Long.valueOf(min), null, Long.valueOf(1)));
  }

  private static JSpinner intSpinner(final int initial) {
    return new JSpinner(new SpinnerNumberModel(initial, null, null, 1));
  }

  private static JSpinner intSpinner(final int min, final int initial) {
    return new JSpinner(new SpinnerNumberModel(initial, min, null, 1));
  }

  private JPanel titled(final String title) {
    final var panel = new JPanel(new GridBagLayout());
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(Color.GRAY), title));
    return panel;
  }

  private void addRow(final JPanel panel, final int row, final String label, final String tooltip,
                      final JComponent field) {
    final var l = new JLabel(label);
    l.setToolTipText(tooltip);
    field.setToolTipText(tooltip);

    final var labelConstraints = new GridBagConstraints();
    labelConstraints.gridx = 0;
    labelConstraints.gridy = row;
    labelConstraints.anchor = GridBagConstraints.WEST;
    labelConstraints.insets = new Insets(4, 4, 4, 8);
    panel.add(l, labelConstraints);

    final var fieldConstraints = new GridBagConstraints();
    fieldConstraints.gridx = 1;
    fieldConstraints.gridy = row;
    fieldConstraints.anchor = GridBagConstraints.WEST;
    fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
    fieldConstraints.weightx = 1;
    fieldConstraints.insets = new Insets(4, 4, 4, 4);
    panel.add(field, fieldConstraints);
  }

}
