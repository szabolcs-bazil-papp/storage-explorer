package com.aestallon.storageexplorer.swing.ui;

import javax.swing.*;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.swing.ui.commander.CommanderView;

@Component
public class AppContentView extends JSplitPane {

  private final MainView mainView;
  private final CommanderView commanderView;
  public AppContentView(MainView mainView, CommanderView commanderView) {
    super(JSplitPane.VERTICAL_SPLIT, mainView, commanderView);
    this.mainView = mainView;
    this.commanderView = commanderView;
    setResizeWeight(1);
    setDividerLocation(0.7);
  }

  public MainView mainView() {
    return mainView;
  }

  public CommanderView commanderView() {
    return commanderView;
  }
}
