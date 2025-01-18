package com.aestallon.storageexplorer.swing.ui.dialog.loadentry;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;

public class LoadEntryDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JTextField textFieldSearchField;
  private JPanel panelControl;
  private JPanel panelForm;
  private JPanel panelField;
  private JLabel labelSearchField;
  private JLabel labelValidation;

  private final LoadEntryController controller;

  private boolean validationState = false;

  public LoadEntryDialog(LoadEntryController controller) {
    this.controller = controller;
    textFieldSearchField.setText(controller.initialModel());

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

    renderValidation();
    textFieldSearchField.getDocument().addDocumentListener(getSearchFieldListener());
  }

  private DocumentListener getSearchFieldListener() {
    return new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        doValidate();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        doValidate();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        doValidate();
      }
    };
  }

  private void doValidate() {
    boolean valid = controller.validate(textFieldSearchField.getText());
    if (valid != validationState) {
      validationState = valid;
      renderValidation();
    }
  }

  private void renderValidation() {
    labelValidation.setIcon(validationState ? IconProvider.OK : IconProvider.NOT_OK);
    labelValidation.setForeground(validationState ? Color.GREEN : Color.RED);
    labelValidation.setText(validationState
        ? "Click OK to load!"
        : "This does not seem like a valid URI...");
    buttonOK.setEnabled(validationState);
  }

  private void onOK() {
    controller.finish(textFieldSearchField.getText());
    dispose();
  }

  private void onCancel() {
    // add your code here if necessary
    dispose();
  }


}
