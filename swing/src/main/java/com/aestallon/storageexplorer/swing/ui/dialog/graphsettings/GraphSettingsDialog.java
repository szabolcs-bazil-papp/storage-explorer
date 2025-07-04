/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.swing.ui.dialog.graphsettings;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

public class GraphSettingsDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JSpinner spinnerOutboundLimit;
  private JSpinner spinnerInbound;
  private JTextArea blacklistSchema;
  private JTextArea blacklistType;
  private JCheckBox discoverNodesOnTheCheckBox;
  private JLabel labelOutbound;
  private JLabel labelInbound;
  private JComboBox comboBoxNodeSizing;
  private JLabel labelBlacklistSchema;
  private JPanel panelBlacklist;
  private JTextArea whitelistSchema;
  private JTextArea whiteListType;
  private JLabel labelWhitelistSchema;
  private JLabel labelBlacklistType;
  private JPanel panelWhitelists;
  private JPanel panelContent;
  private JPanel panelNodeDiscovery;
  private JComboBox comboBoxNodeColouring;
  private JPanel panelRendering;
  private JTextPane nodeDiscoveryHelp;

  private final GraphSettingsController controller;

  public GraphSettingsDialog(GraphSettingsController controller) {
    this.controller = controller;

    final var initialModel = controller.initialModel();
    spinnerInbound.getModel().setValue(initialModel.getGraphTraversalInboundLimit());
    spinnerOutboundLimit.getModel().setValue(initialModel.getGraphTraversalOutboundLimit());
    blacklistSchema.setText(String.join(", ", initialModel.getBlacklistedSchemas()));
    blacklistType.setText(String.join(", ", initialModel.getBlacklistedTypes()));
    whitelistSchema.setText(String.join(", ", initialModel.getWhitelistedSchemas()));
    whiteListType.setText(String.join(", ", initialModel.getWhitelistedTypes()));
    comboBoxNodeSizing.getModel().setSelectedItem(initialModel.getNodeSizing().getValue());
    comboBoxNodeColouring.getModel().setSelectedItem(initialModel.getNodeColouring().getValue());
    discoverNodesOnTheCheckBox.getModel().setSelected(initialModel.getAggressiveDiscovery());

    setTitle("Graph Settings");
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
    contentPane.registerKeyboardAction(
        e -> onCancel(),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void onOK() {
    // TODO: Validate!
    final var model = new GraphSettings()
        .graphTraversalInboundLimit((Integer) spinnerInbound.getModel().getValue())
        .graphTraversalOutboundLimit((Integer) spinnerOutboundLimit.getModel().getValue())
        .blacklistedSchemas(
            Arrays.stream(blacklistSchema.getText().split(",")).map(String::trim).toList())
        .blacklistedTypes(
            Arrays.stream(blacklistType.getText().split(",")).map(String::trim).toList())
        .whitelistedSchemas(
            Arrays.stream(whitelistSchema.getText().split(",")).map(String::trim).toList())
        .whitelistedTypes(
            Arrays.stream(whiteListType.getText().split(",")).map(String::trim).toList())
        .nodeSizing(GraphSettings.NodeSizing.fromValue(
            comboBoxNodeSizing.getModel().getSelectedItem().toString()))
        .nodeColouring(GraphSettings.NodeColouring.fromValue(
            comboBoxNodeColouring.getModel().getSelectedItem().toString()))
        .aggressiveDiscovery(discoverNodesOnTheCheckBox.getModel().isSelected());
    controller.finish(model);
    dispose();
  }

  private void onCancel() {
    // add your code here if necessary
    dispose();
  }

  public static void main(String[] args) {
    GraphSettingsDialog dialog = new GraphSettingsDialog(GraphSettingsController.dummy());
    dialog.pack();
    dialog.setVisible(true);
    System.exit(0);
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
    contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel1,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null,
            null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null,
        0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
    panel1.add(panel2,
        new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
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
    panelContent = new JPanel();
    panelContent.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panelContent,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    panelBlacklist = new JPanel();
    panelBlacklist.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    panelBlacklist.setName("Blacklists");
    panelContent.add(panelBlacklist,
        new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    panelBlacklist.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Blacklists",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
    labelBlacklistSchema = new JLabel();
    labelBlacklistSchema.setText("Schemae");
    panelBlacklist.add(labelBlacklistSchema,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    blacklistSchema = new JTextArea();
    blacklistSchema.setWrapStyleWord(true);
    panelBlacklist.add(blacklistSchema,
        new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
            new Dimension(150, 50), null, 0, false));
    labelBlacklistType = new JLabel();
    labelBlacklistType.setText("Types");
    panelBlacklist.add(labelBlacklistType,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    blacklistType = new JTextArea();
    panelBlacklist.add(blacklistType,
        new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
            new Dimension(150, 50), null, 0, false));
    panelWhitelists = new JPanel();
    panelWhitelists.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    panelContent.add(panelWhitelists,
        new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    panelWhitelists.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Whitelists",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
    labelWhitelistSchema = new JLabel();
    labelWhitelistSchema.setText("Schemae");
    panelWhitelists.add(labelWhitelistSchema,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    whitelistSchema = new JTextArea();
    panelWhitelists.add(whitelistSchema,
        new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
            new Dimension(150, 50), null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("Types");
    panelWhitelists.add(label1,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    whiteListType = new JTextArea();
    panelWhitelists.add(whiteListType,
        new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
            new Dimension(150, 50), null, 0, false));
    panelNodeDiscovery = new JPanel();
    panelNodeDiscovery.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    panelContent.add(panelNodeDiscovery,
        new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    panelNodeDiscovery.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black),
            "Node Discovery", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
            null, null));
    labelOutbound = new JLabel();
    labelOutbound.setText("Outbound edge discovery limit");
    panelNodeDiscovery.add(labelOutbound,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    spinnerOutboundLimit = new JSpinner();
    panelNodeDiscovery.add(spinnerOutboundLimit,
        new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    labelInbound = new JLabel();
    labelInbound.setText("Inbound edge discovery limit");
    panelNodeDiscovery.add(labelInbound,
        new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    spinnerInbound = new JSpinner();
    panelNodeDiscovery.add(spinnerInbound,
        new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    discoverNodesOnTheCheckBox = new JCheckBox();
    discoverNodesOnTheCheckBox.setText("Discover nodes on the go");
    panelNodeDiscovery.add(discoverNodesOnTheCheckBox,
        new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    nodeDiscoveryHelp = new JTextPane();
    nodeDiscoveryHelp.setEditable(false);
    nodeDiscoveryHelp.setEnabled(false);
    nodeDiscoveryHelp.setText(
        "Additional information available on the individual widget tooltips.");
    panelNodeDiscovery.add(nodeDiscoveryHelp,
        new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
            new Dimension(150, 50), null, 0, false));
    panelRendering = new JPanel();
    panelRendering.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    panelContent.add(panelRendering,
        new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    panelRendering.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black),
            "Node Rendering", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
            null, null));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    panelRendering.add(panel3,
        new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Node Sizing Strategy");
    panel3.add(label2,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    comboBoxNodeSizing = new JComboBox();
    final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
    defaultComboBoxModel1.addElement("UNIFORM");
    defaultComboBoxModel1.addElement("OUT DEGREE");
    defaultComboBoxModel1.addElement("IN DEGREE");
    defaultComboBoxModel1.addElement("DEGREE");
    defaultComboBoxModel1.addElement("EST. SIZE");
    defaultComboBoxModel1.addElement("VERSION COUNT");
    comboBoxNodeSizing.setModel(defaultComboBoxModel1);
    panel3.add(comboBoxNodeSizing, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label3 = new JLabel();
    label3.setText("Node Colouring Strategy");
    panel3.add(label3,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    comboBoxNodeColouring = new JComboBox();
    final DefaultComboBoxModel defaultComboBoxModel2 = new DefaultComboBoxModel();
    defaultComboBoxModel2.addElement("UNIFORM");
    defaultComboBoxModel2.addElement("TYPE");
    defaultComboBoxModel2.addElement("SCHEMA");
    defaultComboBoxModel2.addElement("DEGREE");
    defaultComboBoxModel2.addElement("OUT DEGREE");
    defaultComboBoxModel2.addElement("IN DEGREE");
    defaultComboBoxModel2.addElement("SIZE");
    comboBoxNodeColouring.setModel(defaultComboBoxModel2);
    panel3.add(comboBoxNodeColouring, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return contentPane; }

}
