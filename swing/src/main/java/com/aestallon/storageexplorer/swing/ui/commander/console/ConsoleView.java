package com.aestallon.storageexplorer.swing.ui.commander.console;

import java.awt.*;
import java.util.Objects;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;

@Component
public class ConsoleView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(ConsoleView.class);


  @FunctionalInterface
  public interface LogLevelChanger {
    void updateLogLevel(String level);
  }


  private LogLevelChanger logLevelChanger = level -> {};

  private final JTextArea logArea;
  private final JScrollPane scrollPane;

  private final transient MonospaceFontProvider monospaceFontProvider;

  public ConsoleView(MonospaceFontProvider monospaceFontProvider) {
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

    return controlsPanel;
  }

  public void appendLog(String logMessage) {
    SwingUtilities.invokeLater(() -> {
      try {
        final int max = 500;
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

}
