/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package hu.aestallon.storageexplorer.ui.dialog;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.formdev.flatlaf.ui.FlatUIUtils;
import hu.aestallon.storageexplorer.domain.userconfig.model.GraphSettings;
import hu.aestallon.storageexplorer.domain.userconfig.service.UserConfigService;

public class GraphSettingsDialog extends JFrame {

  private final UserConfigService userConfigService;

  public GraphSettingsDialog(UserConfigService userConfigService) {
    this.userConfigService = userConfigService;

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setTitle("Graph Settings");
    add(new UserConfigurationView(userConfigService.graphSettings()));
    pack();
  }

  private final class UserConfigurationView extends JPanel {

    private final JSpinner graphTraversalInboundLimitSpinner;

    private final JSpinner graphTraversalOutboundLimitSpinner;

    private UserConfigurationView(GraphSettings graphSettings) {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setPreferredSize(new Dimension(350, 100));

      final var inboundContainer = new JPanel();
      inboundContainer.setLayout(new GridLayout(1, 2));
      inboundContainer.setBorder(new EmptyBorder(5, 5, 5, 5));
      JLabel graphTraversalInboundLimitLabel = new JLabel("Inbound edge discovery limit");
      graphTraversalInboundLimitLabel.setFont(
          FlatUIUtils.nonUIResource(UIManager.getFont("h4.font")));
      graphTraversalInboundLimitSpinner = new JSpinner(
          new GraphTraversalLimitSpinnerModel(graphSettings.getGraphTraversalInboundLimit()));
      graphTraversalInboundLimitSpinner.setPreferredSize(new Dimension(40, 20));
      graphTraversalInboundLimitSpinner.setToolTipText(
          "Set how many rounds of incoming edges to discover pointing to already rendered nodes.\n"
              + "-1 means unlimited. 0 means incoming edges won't be discovered.");

      inboundContainer.add(graphTraversalInboundLimitLabel);
      inboundContainer.add(graphTraversalInboundLimitSpinner);
      add(inboundContainer);

      final var outboundContainer = new JPanel();
      outboundContainer.setLayout(new GridLayout(1, 2));
      outboundContainer.setBorder(new EmptyBorder(5, 5, 5, 5));
      JLabel graphTraversalOutboundLimitLabel = new JLabel("Outgoing edge discovery limit");
      graphTraversalOutboundLimitLabel.setFont(
          FlatUIUtils.nonUIResource(UIManager.getFont("h4.font")));
      graphTraversalOutboundLimitSpinner = new JSpinner(
          new GraphTraversalLimitSpinnerModel(graphSettings.getGraphTraversalOutboundLimit()));
      graphTraversalOutboundLimitSpinner.setPreferredSize(new Dimension(40, 20));
      graphTraversalOutboundLimitSpinner.setToolTipText(
          "Set how many rounds of how outgoing references to render.\n"
              + "-1 means unlimited. 0 means outgoing edges won't be rendered.");

      outboundContainer.add(graphTraversalOutboundLimitLabel);
      outboundContainer.add(graphTraversalOutboundLimitSpinner);
      add(outboundContainer);

      final var actionContainer = new JPanel();
      actionContainer.setLayout(new BoxLayout(actionContainer, BoxLayout.X_AXIS));
      actionContainer.setPreferredSize(new Dimension(350, 50));

      JButton saveBtn = new JButton("Save");
      saveBtn.addActionListener(e -> {
        GraphSettingsDialog.this.dispose();
        userConfigService.updateGraphSettings(new GraphSettings()
            .graphTraversalInboundLimit(spinnerValue(graphTraversalInboundLimitSpinner))
            .graphTraversalOutboundLimit(spinnerValue(graphTraversalOutboundLimitSpinner)));

      });

      JButton cancelBtn = new JButton("Cancel");
      cancelBtn.addActionListener(e -> GraphSettingsDialog.this.dispose());

      actionContainer.add(Box.createHorizontalGlue());
      actionContainer.add(saveBtn);
      actionContainer.add(cancelBtn);
      add(actionContainer);
    }

  }

  private static int spinnerValue(JSpinner spinner) {
    final var value = spinner.getValue();
    return (value instanceof Integer) ? (Integer) value : 0;
  }

  private static final class GraphTraversalLimitSpinnerModel extends SpinnerNumberModel {

    private GraphTraversalLimitSpinnerModel(final int initialValue) {
      super(initialValue, -1, Integer.MAX_VALUE, 1);
    }

  }

}
