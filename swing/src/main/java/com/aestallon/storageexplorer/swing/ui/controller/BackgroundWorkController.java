package com.aestallon.storageexplorer.swing.ui.controller;

import javax.swing.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkCompletedEvent;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkStartedEvent;
import com.aestallon.storageexplorer.swing.ui.AppContentView;

@Service
public class BackgroundWorkController {
  
  private final AppContentView appContentView;

  public BackgroundWorkController(AppContentView appContentView) {
    this.appContentView = appContentView;
  }
  
  @EventListener
  public void onStarted(final BackgroundWorkStartedEvent event) {
    SwingUtilities.invokeLater(() -> appContentView.showProgressBar(event.displayName()));;
  }

  @EventListener
  public void onCompleted(final BackgroundWorkCompletedEvent event) {
    SwingUtilities.invokeLater(appContentView::removeProgressBar);
  }
  
}
