package hu.aestallon.storageexplorer.ui.toast;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.event.msg.ErrorMsg;
import hu.aestallon.storageexplorer.event.msg.InfoMsg;
import hu.aestallon.storageexplorer.event.msg.Msg;
import hu.aestallon.storageexplorer.event.msg.WarnMsg;
import hu.aestallon.storageexplorer.ui.AppFrame;
import hu.aestallon.storageexplorer.ui.misc.Severity;

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
    final JPanel toast = new ToastView(severity, msg.title(), msg.message()).getMainPanel();
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
    timer.setRepeats(false);
    timer.start();
  }

  private void removeToast(JPanel toast) {
    JLayeredPane layeredPane = appFrame.getLayeredPane();
    layeredPane.remove(toast);
    activeToasts.remove(toast);

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
  
  

}
