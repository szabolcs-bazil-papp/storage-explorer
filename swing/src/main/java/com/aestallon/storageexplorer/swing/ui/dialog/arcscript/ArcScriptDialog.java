package com.aestallon.storageexplorer.swing.ui.dialog.arcscript;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.api.ArcScript;
import com.aestallon.storageexplorer.core.event.StorageReindexed;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

public class ArcScriptDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JTextPane codeEditor;

  private final StorageInstance storageInstance;
  private final ApplicationEventPublisher eventPublisher;

  public ArcScriptDialog(final StorageInstance storageInstance,
                         final ApplicationEventPublisher eventPublisher) {
    this.storageInstance = storageInstance;
    this.eventPublisher = eventPublisher;
    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);

    buttonOK.addActionListener(e -> onOK());

    buttonCancel.addActionListener(e -> onCancel());

    // call onCancel() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });

    // call onCancel() on ESCAPE
    contentPane.registerKeyboardAction(e -> onCancel(),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void onOK() {
    final String code = codeEditor.getText();
    final var parse = Arc.parse(code);
    ArcScript script = Arc.evaluate(parse);
    Arc.execute(script, storageInstance);
    eventPublisher.publishEvent(new StorageReindexed(storageInstance));
  }

  private void onCancel() {
    dispose();
  }

  public static void main(String[] args) {
    ArcScriptDialog dialog = new ArcScriptDialog(null, null);
    dialog.pack();
    dialog.setVisible(true);
    System.exit(0);
  }

}
