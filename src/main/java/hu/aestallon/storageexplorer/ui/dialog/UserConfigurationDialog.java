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
import com.formdev.flatlaf.ui.FlatUIUtils;
import hu.aestallon.storageexplorer.domain.userconfig.model.UserConfig;
import hu.aestallon.storageexplorer.domain.userconfig.service.UserConfigService;

public class UserConfigurationDialog extends JFrame {

  private final UserConfigService userConfigService;

  public UserConfigurationDialog(UserConfigService userConfigService) {
    this.userConfigService = userConfigService;

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setTitle("Settings");

    add(new UserConfigurationView(userConfigService.get()));
    pack();
  }

  private final class UserConfigurationView extends JPanel {

    private JLabel graphTraversalInboundLimitLabel;
    private JSpinner graphTraversalInboundLimitSpinner;
    private JLabel graphTraversalInboundLimitHint;

    private JLabel graphTraversalOutboundLimitLabel;
    private JSpinner graphTraversalOutboundLimitSpinner;
    private JLabel graphTraversalOutboundLimitHint;

    private JButton saveBtn;
    private JButton cancelBtn;

    private final UserConfig userConfig;

    private UserConfigurationView(UserConfig userConfig) {
      this.userConfig = userConfig;

      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setPreferredSize(new Dimension(500, 500));

      final var inboundContainer = new JPanel();
      inboundContainer.setLayout(new BoxLayout(inboundContainer, BoxLayout.X_AXIS));
      graphTraversalInboundLimitLabel = new JLabel("Inbound edge discovery limit");
      graphTraversalInboundLimitLabel.setFont(
          FlatUIUtils.nonUIResource(UIManager.getFont("h4.font")));
      graphTraversalInboundLimitSpinner = new JSpinner(
          new GraphTraversalLimitSpinnerModel(userConfig.getGraphTraversalInboundLimit()));
      graphTraversalInboundLimitSpinner.setPreferredSize(new Dimension(40, 20));
      graphTraversalInboundLimitHint = new JLabel(
          "Set how many rounds of incoming edges to discover pointing to already rendered nodes. -1 means unlimited. 0 means incoming edges won't be discovered.");
      graphTraversalInboundLimitHint.setFont(
          FlatUIUtils.nonUIResource(UIManager.getFont("mini.font")));

      inboundContainer.add(graphTraversalInboundLimitLabel);
      inboundContainer.add(graphTraversalInboundLimitSpinner);
      inboundContainer.add(Box.createHorizontalGlue());
      inboundContainer.add(graphTraversalInboundLimitHint);
      add(inboundContainer);

      final var outboundContainer = new JPanel();
      outboundContainer.setLayout(new BoxLayout(outboundContainer, BoxLayout.X_AXIS));
      graphTraversalOutboundLimitLabel = new JLabel("Outgoing edge discovery limit");
      graphTraversalOutboundLimitLabel.setFont(
          FlatUIUtils.nonUIResource(UIManager.getFont("h4.font")));
      graphTraversalOutboundLimitSpinner = new JSpinner(
          new GraphTraversalLimitSpinnerModel(userConfig.getGraphTraversalOutboundLimit()));
      graphTraversalOutboundLimitSpinner.setPreferredSize(new Dimension(40, 20));
      graphTraversalOutboundLimitHint = new JLabel(
          "Set how many rounds of how outgoing references to render. -1 means unlimited. 0 means outgoing edges won't be rendered.");
      graphTraversalOutboundLimitHint.setFont(
          FlatUIUtils.nonUIResource(UIManager.getFont("mini.font")));

      outboundContainer.add(graphTraversalOutboundLimitLabel);
      outboundContainer.add(graphTraversalOutboundLimitSpinner);
      outboundContainer.add(Box.createHorizontalGlue());
      outboundContainer.add(graphTraversalOutboundLimitHint);
      add(outboundContainer);

      final var actionContainer = new JPanel();
      actionContainer.setLayout(new BoxLayout(actionContainer, BoxLayout.X_AXIS));
      saveBtn = new JButton("Save");
      cancelBtn = new JButton("Cancel");
      actionContainer.add(Box.createHorizontalGlue());
      actionContainer.add(saveBtn);
      actionContainer.add(cancelBtn);
      add(actionContainer);
    }

  }


  private static final class GraphTraversalLimitSpinnerModel extends SpinnerNumberModel {

    private GraphTraversalLimitSpinnerModel(final int initialValue) {
      super(initialValue, -1, Integer.MAX_VALUE, 1);
    }

  }
}
