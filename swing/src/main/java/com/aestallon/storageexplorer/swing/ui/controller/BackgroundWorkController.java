package com.aestallon.storageexplorer.swing.ui.controller;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkCompletedEvent;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkStartedEvent;
import com.aestallon.storageexplorer.core.event.LoadingQueueSize;
import com.aestallon.storageexplorer.swing.ui.AppContentView;

@Service
public class BackgroundWorkController {

  private final AppContentView appContentView;

  private final ConcurrentHashMap<UUID, String> runningTasks = new ConcurrentHashMap<>();

  public BackgroundWorkController(AppContentView appContentView) {
    this.appContentView = appContentView;
  }

  @EventListener
  public void onStarted(final BackgroundWorkStartedEvent event) {
    runningTasks.put(event.uuid(), event.displayName());
    final int size = runningTasks.size();
    SwingUtilities.invokeLater(() -> appContentView.showProgressBar(size > 1
        ? event.displayName() + " (and " + (size - 1) + " more)"
        : event.displayName()));
  }

  @EventListener
  public void onCompleted(final BackgroundWorkCompletedEvent event) {
    runningTasks.remove(event.uuid());
    final int size = runningTasks.size();
    if (size > 0) {
      final String name = runningTasks.values().stream().findFirst().get();
      SwingUtilities.invokeLater(() -> appContentView.showProgressBar(size > 1
          ? name + " (and " + (size - 1) + " more)"
          : name));
    } else {
      SwingUtilities.invokeLater(appContentView::removeProgressBar);
    }
  }

  @EventListener
  public void onLoadingQueueSizeChanged(final LoadingQueueSize event) {
    SwingUtilities.invokeLater(() -> appContentView.setLoadingQueueSize(event));
  }

}
