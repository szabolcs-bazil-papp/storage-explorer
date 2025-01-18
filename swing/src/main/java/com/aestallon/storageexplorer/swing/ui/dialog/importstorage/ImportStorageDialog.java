package com.aestallon.storageexplorer.swing.ui.dialog.importstorage;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileSystemView;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseConnectionData;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseVendor;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;

public class ImportStorageDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JTabbedPane storageTypePane;
  private JTextField textFieldFsRootDir;
  private JButton buttonFsRootDirLookup;
  private JTextField textFieldDbUrl;
  private JTextField textFieldDbUsername;
  private JTextField textFieldDbPassword;
  private JPanel databaseConnectionForm;
  private JTextField textFieldSshAddress;
  private JTextField textFieldSshPort;
  private JTextField textFieldSshUsername;
  private JTextField textFieldSshPassword;
  private JCheckBox checkboxRememberDbPassword;
  private JCheckBox checkboxRememberSshPassword;
  private JTextField textFieldStorageName;
  private JLabel labelSshPassword;
  private JLabel labelSshUsername;
  private JLabel labelSshPort;
  private JLabel labelSshAddress;
  private JLabel labelDbPassword;
  private JLabel labelDbUsername;
  private JLabel labelDbUrl;
  private JPanel sqlPane;
  private JPanel corePropertiesPane;
  private JLabel labelStorageName;
  private JPanel sshForm;
  private JLabel labelFsRootDir;
  private JLabel labelFsHelp;
  private JPanel fsPane;
  private JPanel indexingBehaviourPane;
  private JRadioButton noIndex;
  private JRadioButton indexEntries;
  private JRadioButton fullIndex;
  private JTextPane entriesShallBeIndexedTextPane;
  private JTextPane noIndexingShallBeTextPane;
  private JTextPane allEntriesAndTheirTextPane;
  private JTextField textFieldSchema;
  private JLabel labelSchema;

  private final ImportStorageController controller;

  public ImportStorageDialog(final ImportStorageController controller) {
    this.controller = controller;

    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);

    StorageInstanceDto storageInstanceDto = controller.initialModel();
    final boolean editing = storageInstanceDto.getId() != null;
    setTitle(editing
        ? "Edit Storage (" + storageInstanceDto.getName() + ")"
        : "Import Storage");
    if (!editing) {
      indexEntries.setSelected(true);
    } else {
      switch (storageInstanceDto.getIndexingStrategy()) {
        case ON_DEMAND:
          noIndex.setSelected(true);
          break;
        case INITIAL:
          indexEntries.setSelected(true);
          break;
        case FULL:
          fullIndex.setSelected(true);
          break;
      }

      if (storageInstanceDto.getType() == StorageInstanceType.DB) {
        storageTypePane.setSelectedIndex(1);
        final DatabaseConnectionData connectionData = storageInstanceDto
            .getDb()
            .getDbConnectionData();
        textFieldDbUrl.setText(connectionData.getUrl());
        textFieldDbUsername.setText(connectionData.getUsername());
        textFieldDbPassword.setText(connectionData.getPassword());
        textFieldSchema.setText(connectionData.getTargetSchema());
      } else {
        storageTypePane.setSelectedIndex(0);
        textFieldFsRootDir.setText(storageInstanceDto.getFs().getPath().toString());
      }
      storageTypePane.setEnabledAt(0, false);
      storageTypePane.setEnabledAt(1, false);

      textFieldStorageName.setText(storageInstanceDto.getName());
    }

    buttonOK.addActionListener(e -> onOK());

    buttonCancel.addActionListener(e -> onCancel());
    buttonFsRootDirLookup.addActionListener(e -> onFsRootDirLookup());

    // call onCancel() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });

    // call onCancel() on ESCAPE
    contentPane.registerKeyboardAction(e ->
            onCancel(),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void onOK() {
    final StorageInstanceType storageInstanceType = getStorageInstanceType();
    final StorageLocation location = getAndValidateStorageLocation(storageInstanceType);
    final String name = textFieldStorageName.getText();
    final IndexingStrategyType indexingStrategy = getIndexingStrategy();
    final var result = new StorageInstanceDto()
        .id(controller.initialModel().getId())
        .name(name)
        .indexingStrategy(indexingStrategy)
        .type(storageInstanceType);
    if (location instanceof FsStorageLocation) {
      result.setFs((FsStorageLocation) location);
    } else if (location instanceof SqlStorageLocation) {
      result.setDb((SqlStorageLocation) location);
    } else {
      throw new IllegalArgumentException("Unsupported location type: " + location);
    }

    controller.finish(result);
    dispose();
  }

  private IndexingStrategyType getIndexingStrategy() {
    if (noIndex.isSelected()) {
      return IndexingStrategyType.ON_DEMAND;
    }

    if (indexEntries.isSelected()) {
      return IndexingStrategyType.INITIAL;
    }

    if (fullIndex.isSelected()) {
      return IndexingStrategyType.FULL;
    }

    throw new IllegalStateException("No indexing strategy selected!");
  }

  private StorageInstanceType getStorageInstanceType() {
    final int typeIdx = storageTypePane.getSelectedIndex();
    return switch (typeIdx) {
      case 0 -> StorageInstanceType.FS;
      case 1 -> StorageInstanceType.DB;
      default -> throw new IllegalStateException("Unexpected value: " + typeIdx);
    };
  }

  private StorageLocation getAndValidateStorageLocation(StorageInstanceType storageInstanceType) {
    if (StorageInstanceType.FS == storageInstanceType) {
      return new FsStorageLocation().path(Path.of(textFieldFsRootDir.getText()));

    } else if (StorageInstanceType.DB == storageInstanceType) {
      return new SqlStorageLocation()
          .vendor(textFieldDbUrl.getText().contains("postgres")
              ? DatabaseVendor.PG
              : DatabaseVendor.ORACLE)
          .dbConnectionData(new DatabaseConnectionData()
              .url(textFieldDbUrl.getText())
              .username(textFieldDbUsername.getText())
              .password(textFieldDbPassword.getText())
              .targetSchema(textFieldSchema.getText()));

    } else {
      throw new IllegalArgumentException("Unsupported storage type: " + storageInstanceType);

    }
  }

  private void onCancel() {
    // add your code here if necessary
    dispose();
  }

  private void onFsRootDirLookup() {
    final var fileChooser = new JFileChooser(FileSystemView.getFileSystemView());
    fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    fileChooser.setDialogTitle("Import Storage...");
    fileChooser.setCurrentDirectory(new File("."));

    final int result = fileChooser.showDialog(this, "Import");
    if (JFileChooser.APPROVE_OPTION == result) {
      final File selectedFile = fileChooser.getSelectedFile();
      if (!selectedFile.isDirectory()) {
        System.err.println("REEEE");
        return;
      }

      textFieldFsRootDir.setText(selectedFile.getAbsolutePath());
    }
  }

  public static void main(String[] args) {
    new ImportStorageDialog(
        new ImportStorageController(new StorageInstanceDto().fs(new FsStorageLocation().path(
            Paths.get(""))), (a, b) -> {})).show();
  }

}
