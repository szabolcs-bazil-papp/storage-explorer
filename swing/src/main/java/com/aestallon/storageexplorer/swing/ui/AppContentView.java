package com.aestallon.storageexplorer.swing.ui;

import java.awt.*;
import javax.swing.*;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.swing.ui.commander.CommanderView;

@Component
public class AppContentView extends JPanel {

  private final MainView mainView;
  private final CommanderView commanderView;
  private final JSplitPane content;
  private final JToolBar toolBar;
  private JProgressBar progressBar;

  public AppContentView(MainView mainView, CommanderView commanderView) {
    this.mainView = mainView;
    this.commanderView = commanderView;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    content = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainView, commanderView);
    content.setResizeWeight(1);
    content.setDividerLocation(0.7);
    content.setAlignmentX(0);
    add(content);

    toolBar = initToolBar();
    toolBar.setAlignmentX(0);
    add(toolBar);

  }

  private JToolBar initToolBar() {
    final var tb = new JToolBar(SwingConstants.HORIZONTAL);
    tb.add(new JLabel("Storage Explorer"));
    tb.addSeparator();
    tb.add(Box.createGlue());
    return tb;
  }

  public void showProgressBar(final String displayName) {
    if (progressBar == null) {
      progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
      progressBar.setPreferredSize(new Dimension(300, 20));
    }
    progressBar.setString(displayName);
    progressBar.setStringPainted(true);
    progressBar.setIndeterminate(true);
    toolBar.add(progressBar, 2);
    toolBar.updateUI();
  }

  public void removeProgressBar() {
    if (progressBar == null) {
      return;
    }
    toolBar.remove(progressBar);
    toolBar.updateUI();
  }

  public MainView mainView() {
    return mainView;
  }

  public CommanderView commanderView() {
    return commanderView;
  }
}
