package com.aestallon.storageexplorer.swing.ui.commander.console;

import java.awt.*;
import java.util.Objects;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.commander.AbstractCommanderPanelView;
import com.aestallon.storageexplorer.swing.ui.commander.CommanderView;
import com.aestallon.storageexplorer.swing.ui.controller.SideBarController;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;

@Component
public class ConsoleView extends AbstractCommanderPanelView implements CommanderView {

  private static final Logger log = LoggerFactory.getLogger(ConsoleView.class);

  private static final int DEFAULT_CAP = 100;

  @FunctionalInterface
  public interface LogLevelChanger {
    void updateLogLevel(String level);
  }


  private transient LogLevelChanger logLevelChanger = level -> {};

  private final JTextArea logArea;
  private final JScrollPane scrollPane;
  
  private volatile int cap = DEFAULT_CAP;

  private final transient MonospaceFontProvider monospaceFontProvider;

  public ConsoleView(UserConfigService userConfigService,
                     SideBarController sideBarController,
                     MonospaceFontProvider monospaceFontProvider) {
    super(userConfigService, sideBarController);
    this.monospaceFontProvider = monospaceFontProvider;
    setLayout(new BorderLayout());

    logArea = new JTextArea();
    logArea.setEditable(false);
    logArea.setLineWrap(true);
    logArea.setWrapStyleWord(true);
    logArea.setOpaque(false);
    logArea.setFont(monospaceFontProvider.getFont());
    monospaceFontProvider.applyFontSizeChangeAction(logArea);

    scrollPane = new JScrollPane(logArea);
    add(scrollPane, BorderLayout.CENTER);

    // Add controls panel at the bottom
    add(createControlsPanel(), BorderLayout.SOUTH);
  }

  public void setLogLevelChanger(final LogLevelChanger logLevelChanger) {
    this.logLevelChanger = Objects.requireNonNull(logLevelChanger);
  }

  @EventListener
  public void onFontSizeChanged(final MonospaceFontProvider.FontSizeChange event) {
    SwingUtilities.invokeLater(() -> {
      logArea.setFont(monospaceFontProvider.getFont());
    });
  }

  private JPanel createControlsPanel() {
    JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    JComboBox<String> logLevelCombo = new JComboBox<>(
        new String[] { "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF" }
    );
    logLevelCombo.setSelectedItem("INFO");
    logLevelCombo.addActionListener(e -> {
      String level = (String) logLevelCombo.getSelectedItem();
      logLevelChanger.updateLogLevel(level);
    });
    controlsPanel.add(new JLabel("Log Level:"));
    controlsPanel.add(logLevelCombo);
    
    final JComboBox<Integer> capCombo = new JComboBox<>(
        new Integer[] { 50, DEFAULT_CAP, 200, 500, 1000, 2000 }
    );
    capCombo.setSelectedItem(DEFAULT_CAP);
    capCombo.addActionListener(e -> {
      final Integer curr = (Integer) capCombo.getSelectedItem();
      assert curr != null;
      if (curr != cap) {
        cap = curr;
      }
    });
    controlsPanel.add(new JLabel("Max lines:"));
    controlsPanel.add(capCombo);

    return controlsPanel;
  }

  public void appendLog(String logMessage) {
    final int max = cap;
    SwingUtilities.invokeLater(() -> {
      try {
        final int lineCount = logArea.getLineCount();
        if (lineCount >= max) {
          logArea.replaceRange(
              null,
              logArea.getLineStartOffset(0),
              logArea.getLineEndOffset(lineCount - max));
        }
        logArea.append(logMessage);
      } catch (final BadLocationException e) {
        log.error(e.getMessage(), e);
      }
    });
  }

  @Override
  public String name() {
    return "Console Commander";
  }

  @Override
  public ImageIcon icon() {
    return IconProvider.TERMINAL;
  }

  @Override
  public String tooltip() {
    return "Application logs";
  }

}
