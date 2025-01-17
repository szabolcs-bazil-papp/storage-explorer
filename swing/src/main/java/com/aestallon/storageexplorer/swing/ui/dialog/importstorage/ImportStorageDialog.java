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
    final StorageInstanceType storageInstanceType;
    switch (typeIdx) {
      case 0:
        storageInstanceType = StorageInstanceType.FS;
        break;
      case 1:
        storageInstanceType = StorageInstanceType.DB;
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + typeIdx);
    }
    return storageInstanceType;
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

  {
    // GUI initializer generated by IntelliJ IDEA GUI Designer
    // >>> IMPORTANT!! <<<
    // DO NOT EDIT OR ADD ANY CODE HERE!
    $$$setupUI$$$();
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR
   * call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    contentPane = new JPanel();
    contentPane.setLayout(new GridBagLayout());
    contentPane.setMinimumSize(new Dimension(700, 674));
    contentPane.setPreferredSize(new Dimension(700, 674));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridBagLayout());
    GridBagConstraints gbc;
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 1.0;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.insets = new Insets(15, 0, 15, 5);
    contentPane.add(panel1, gbc);
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    panel1.add(panel2, gbc);
    buttonOK = new JButton();
    buttonOK.setText("OK");
    panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    buttonCancel = new JButton();
    buttonCancel.setText("Cancel");
    panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridBagLayout());
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    contentPane.add(panel3, gbc);
    storageTypePane = new JTabbedPane();
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    panel3.add(storageTypePane, gbc);
    fsPane = new JPanel();
    fsPane.setLayout(new GridLayoutManager(3, 1, new Insets(5, 5, 5, 5), -1, -1));
    storageTypePane.addTab("File System",
        new ImageIcon(getClass().getResource("/icons/object.png")), fsPane);
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    fsPane.add(panel4,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    labelFsHelp = new JLabel();
    labelFsHelp.setText("Select where your storage is");
    panel4.add(labelFsHelp,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    final JPanel panel5 = new JPanel();
    panel5.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    fsPane.add(panel5,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    labelFsRootDir = new JLabel();
    labelFsRootDir.setText("Storage root folder:");
    panel5.add(labelFsRootDir,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    textFieldFsRootDir = new JTextField();
    panel5.add(textFieldFsRootDir, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    buttonFsRootDirLookup = new JButton();
    buttonFsRootDirLookup.setText("Find...");
    panel5.add(buttonFsRootDirLookup, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    fsPane.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0,
        false));
    sqlPane = new JPanel();
    sqlPane.setLayout(new GridLayoutManager(3, 1, new Insets(5, 5, 5, 5), -1, -1));
    storageTypePane.addTab("SQL ", new ImageIcon(getClass().getResource("/icons/db.png")), sqlPane);
    databaseConnectionForm = new JPanel();
    databaseConnectionForm.setLayout(new GridLayoutManager(3, 5, new Insets(0, 0, 0, 0), -1, -1));
    sqlPane.add(databaseConnectionForm,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    databaseConnectionForm.setBorder(
        BorderFactory.createTitledBorder(null, "DB Connection", TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION, null, null));
    labelDbUrl = new JLabel();
    labelDbUrl.setText("DB URL");
    databaseConnectionForm.add(labelDbUrl,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    textFieldDbUrl = new JTextField();
    databaseConnectionForm.add(textFieldDbUrl,
        new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    labelDbUsername = new JLabel();
    labelDbUsername.setText("Username");
    databaseConnectionForm.add(labelDbUsername,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    textFieldDbUsername = new JTextField();
    databaseConnectionForm.add(textFieldDbUsername,
        new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    labelDbPassword = new JLabel();
    labelDbPassword.setText("Password");
    databaseConnectionForm.add(labelDbPassword,
        new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    textFieldDbPassword = new JTextField();
    textFieldDbPassword.setText("");
    databaseConnectionForm.add(textFieldDbPassword,
        new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(247, 34), null, 0, false));
    checkboxRememberDbPassword = new JCheckBox();
    checkboxRememberDbPassword.setText("remember");
    databaseConnectionForm.add(checkboxRememberDbPassword,
        new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    labelSchema = new JLabel();
    labelSchema.setText("Schema");
    databaseConnectionForm.add(labelSchema,
        new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    textFieldSchema = new JTextField();
    databaseConnectionForm.add(textFieldSchema,
        new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    sshForm = new JPanel();
    sshForm.setLayout(new GridLayoutManager(3, 4, new Insets(0, 0, 0, 0), -1, -1));
    sqlPane.add(sshForm,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    sshForm.setBorder(
        BorderFactory.createTitledBorder(null, "SSH", TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION, null, null));
    labelSshAddress = new JLabel();
    labelSshAddress.setText("Target IP");
    sshForm.add(labelSshAddress,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    textFieldSshAddress = new JTextField();
    textFieldSshAddress.setEditable(false);
    textFieldSshAddress.setEnabled(false);
    sshForm.add(textFieldSshAddress, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(240, 34), null, 0, false));
    labelSshPort = new JLabel();
    labelSshPort.setText("Port");
    sshForm.add(labelSshPort,
        new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
            new Dimension(36, 17), null, 0, false));
    textFieldSshPort = new JTextField();
    textFieldSshPort.setEditable(false);
    textFieldSshPort.setEnabled(false);
    sshForm.add(textFieldSshPort, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), null, 0, false));
    labelSshUsername = new JLabel();
    labelSshUsername.setText("Username");
    sshForm.add(labelSshUsername,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    textFieldSshUsername = new JTextField();
    textFieldSshUsername.setEditable(false);
    textFieldSshUsername.setEnabled(false);
    sshForm.add(textFieldSshUsername, new GridConstraints(1, 1, 1, 3, GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    labelSshPassword = new JLabel();
    labelSshPassword.setText("Password");
    sshForm.add(labelSshPassword,
        new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    textFieldSshPassword = new JTextField();
    textFieldSshPassword.setEditable(false);
    textFieldSshPassword.setEnabled(false);
    sshForm.add(textFieldSshPassword, new GridConstraints(2, 1, 1, 2, GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    checkboxRememberSshPassword = new JCheckBox();
    checkboxRememberSshPassword.setEnabled(false);
    checkboxRememberSshPassword.setText("remember");
    sshForm.add(checkboxRememberSshPassword,
        new GridConstraints(2, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    sqlPane.add(spacer2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0,
        false));
    corePropertiesPane = new JPanel();
    corePropertiesPane.setLayout(new GridBagLayout());
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(5, 5, 5, 5);
    panel3.add(corePropertiesPane, gbc);
    corePropertiesPane.setBorder(BorderFactory.createTitledBorder(null, "Storage Properties",
        TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
    labelStorageName = new JLabel();
    labelStorageName.setText("Storage name");
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weighty = 1.0;
    gbc.anchor = GridBagConstraints.WEST;
    corePropertiesPane.add(labelStorageName, gbc);
    textFieldStorageName = new JTextField();
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(0, 5, 0, 0);
    corePropertiesPane.add(textFieldStorageName, gbc);
    indexingBehaviourPane = new JPanel();
    indexingBehaviourPane.setLayout(new GridLayoutManager(3, 6, new Insets(0, 0, 0, 0), -1, -1));
    indexingBehaviourPane.setMinimumSize(new Dimension(700, 209));
    indexingBehaviourPane.setPreferredSize(new Dimension(700, 209));
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    corePropertiesPane.add(indexingBehaviourPane, gbc);
    indexingBehaviourPane.setBorder(BorderFactory.createTitledBorder(null, "Indexing behaviour",
        TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
    noIndex = new JRadioButton();
    noIndex.setEnabled(true);
    noIndex.setText("No Index");
    indexingBehaviourPane.add(noIndex,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    indexEntries = new JRadioButton();
    indexEntries.setEnabled(true);
    indexEntries.setSelected(false);
    indexEntries.setText("Index Entries");
    indexingBehaviourPane.add(indexEntries,
        new GridConstraints(1, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    fullIndex = new JRadioButton();
    fullIndex.setEnabled(true);
    fullIndex.setText("Full Index");
    indexingBehaviourPane.add(fullIndex,
        new GridConstraints(2, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    entriesShallBeIndexedTextPane = new JTextPane();
    entriesShallBeIndexedTextPane.setEditable(false);
    entriesShallBeIndexedTextPane.setEnabled(false);
    entriesShallBeIndexedTextPane.setText(
        "Entries shall be indexed upon storage import. This is a fairly quick operation on even large storages, and instantenous on smaller ones. Connections between entries will be dynamically discovered when an entry is inspected. Use this indexing strategy when dealing with larger storage instances.");
    indexingBehaviourPane.add(entriesShallBeIndexedTextPane,
        new GridConstraints(1, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
            new Dimension(150, 50), null, 0, false));
    noIndexingShallBeTextPane = new JTextPane();
    noIndexingShallBeTextPane.setEditable(false);
    noIndexingShallBeTextPane.setEnabled(false);
    noIndexingShallBeTextPane.setText(
        "No indexing shall be performed upon storage import, resulting in rapid setup, but no information about entries will be readily available. Use this indexing strategy when you have a specific URI to search for, and want to dynamically discover connections originating from your known entry.");
    indexingBehaviourPane.add(noIndexingShallBeTextPane,
        new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
            new Dimension(150, 50), null, 0, false));
    allEntriesAndTheirTextPane = new JTextPane();
    allEntriesAndTheirTextPane.setEditable(false);
    allEntriesAndTheirTextPane.setEnabled(false);
    allEntriesAndTheirTextPane.setText(
        "All entries and their connecting edges shall be indexed upon storage import. This provides the best opportunity for inbound edge inspection, but may take a long time to perform for larger storages. Use it with smaller storages and when insight is imperative.");
    indexingBehaviourPane.add(allEntriesAndTheirTextPane,
        new GridConstraints(2, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
            new Dimension(150, 50), null, 0, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(fullIndex);
    buttonGroup.add(indexEntries);
    buttonGroup.add(noIndex);
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() {return contentPane;}

}
