package com.aestallon.storageexplorer.swing.ui.toast;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.common.event.msg.ErrorMsg;
import com.aestallon.storageexplorer.common.event.msg.InfoMsg;
import com.aestallon.storageexplorer.common.event.msg.Msg;
import com.aestallon.storageexplorer.common.event.msg.WarnMsg;
import com.aestallon.storageexplorer.swing.ui.AppFrame;
import com.aestallon.storageexplorer.swing.ui.misc.Severity;

@Service
public class ToastService {

  private static final int TOAST_WIDTH = 400;
  private static final int TOAST_HEIGHT = 130;
  private static final int TOAST_GAP = 10;

  private final AppFrame appFrame;
  private final List<JPanel> activeToasts;

  public ToastService(AppFrame appFrame) {
    this.appFrame = appFrame;
    activeToasts = new ArrayList<>();
  }

  @EventListener
  public void onInfoEvent(InfoMsg msg) {
    showToast(msg);
  }

  @EventListener
  public void onWarnEvent(WarnMsg msg) {
    showToast(msg);
  }

  @EventListener
  public void onErrEvent(ErrorMsg msg) {
    showToast(msg);
  }

  public void showToast(final Msg msg) {
    final Severity severity = switch (msg) {
      case InfoMsg info -> Severity.INFO;
      case WarnMsg warn -> Severity.WARNING;
      case ErrorMsg err -> Severity.ERROR;
    };
    ToastView toastView = new ToastView(severity, msg.title(), msg.message());
    final JPanel toast = toastView.getMainPanel();
    toastView.getCloseButton().addActionListener(e -> removeToast(toast));
    addToast(toast);
  }

  private void addToast(JPanel toast) {
    JLayeredPane layeredPane = appFrame.getLayeredPane();
    int x = appFrame.getWidth() - TOAST_WIDTH - 3 * TOAST_GAP;
    int y = TOAST_GAP + (activeToasts.size() * (TOAST_HEIGHT + TOAST_GAP));
    toast.setBounds(x, y, TOAST_WIDTH, TOAST_HEIGHT);

    activeToasts.addFirst(toast);
    layeredPane.add(toast, JLayeredPane.POPUP_LAYER);
    repositionToasts();
    layeredPane.revalidate();
    layeredPane.repaint();

    Timer timer = new Timer(3000, e -> removeToast(toast));
    toast.addMouseListener(timerListener(timer));
    timer.setRepeats(false);
    timer.start();
  }

  private void removeToast(JPanel toast) {
    final boolean removed = activeToasts.remove(toast);
    if (!removed) {
      return;
    }

    JLayeredPane layeredPane = appFrame.getLayeredPane();
    layeredPane.remove(toast);

    repositionToasts();
    layeredPane.revalidate();
    layeredPane.repaint();
  }

  private void repositionToasts() {
    int x = appFrame.getWidth() - TOAST_WIDTH - 3 * TOAST_GAP;
    for (int i = 0; i < activeToasts.size(); i++) {
      JPanel toast = activeToasts.get(i);
      int y = TOAST_GAP + (i * (TOAST_HEIGHT + TOAST_GAP));
      toast.setBounds(x, y, TOAST_WIDTH, TOAST_HEIGHT);
    }
  }

  private static MouseAdapter timerListener(Timer timer) {
    return new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        if (timer.isRunning()) {
          timer.stop();
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        if (timer.isRunning()) {
          timer.stop();
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (!timer.isRunning()) {
          timer.restart();
        }
      }
    };
  }

}
