package com.aestallon.storageexplorer.swing.ui.toast;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.Severity;

public class ToastView {
  private JLabel title;
  private JTextPane message;
  private JButton button1;
  private JPanel mainPanel;

  public ToastView(final Severity severity, final String title, final String message) {
    final Border border;
    final ImageIcon icon;
    switch (severity) {
      case ERROR -> {
        border = new LineBorder(Color.RED, 2, true);
        icon = IconProvider.ERROR;
      }
      case WARNING -> {
        border = new LineBorder(new Color(0xFF, 213, 79), 2, true);
        icon = IconProvider.WARNING;
      }
      case INFO -> {
        border = new LineBorder(new Color(66, 148, 255), 2, true);
        icon = IconProvider.INFO;
      }
      case null, default -> throw new IllegalArgumentException("Severity must not be null!");
    }

    this.title.setIcon(icon);
    this.title.setText(title);
    this.title.setFont(UIManager.getFont("h4.font"));
    mainPanel.setBorder(border);
    this.message.setText(message);
  }

  JPanel getMainPanel() {
    return mainPanel;
  }
  
  JButton getCloseButton() {
    return button1;
  }

}
