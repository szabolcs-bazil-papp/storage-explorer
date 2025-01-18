package com.aestallon.storageexplorer.swing.ui.commander;

import java.awt.*;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.core.service.StorageInstanceProvider;
import com.aestallon.storageexplorer.swing.ui.commander.arcscript.ArcScriptContainerView;

@Component
public class CommanderView extends JTabbedPane {

  private static final Logger log = LoggerFactory.getLogger(CommanderView.class);

  private final ArcScriptContainerView arcScriptContainerView;

  public CommanderView(ArcScriptContainerView arcScriptContainerView) {
    super(JTabbedPane.LEFT, JTabbedPane.SCROLL_TAB_LAYOUT);
    this.arcScriptContainerView = arcScriptContainerView;
    
    initArcScriptTab();
  }
  
  private void initArcScriptTab() {
    addTab("AS", arcScriptContainerView);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    int width = getWidth();
    int height = getHeight();
    Graphics2D graphics = (Graphics2D) g;

    //Sets antialiasing if HQ.
    graphics.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);


    //Draws the rounded opaque panel with borders.
    graphics.setColor(getBackground());
    graphics.fillRoundRect(0, 0, width - 1,
        height - 1, 20, 20);
    graphics.setColor(getBackground().darker());
    graphics.setStroke(new BasicStroke(2));
    graphics.drawRoundRect(0, 0, width - 1,
        height - 1, 20, 20);

    //Sets strokes to default, is better.
    graphics.setStroke(new BasicStroke());
  }
}
