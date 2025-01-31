package com.aestallon.storageexplorer.swing.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.common.util.MsgStrings;
import com.aestallon.storageexplorer.swing.ui.commander.CommanderView;
import com.aestallon.storageexplorer.swing.ui.event.BreadCrumbsChanged;

@Component
public class AppContentView extends JPanel {

  private final MainView mainView;
  private final CommanderView commanderView;
  private final JSplitPane content;
  private final JToolBar toolBar;
  private final BreadCrumbs breadCrumbs;
  private JProgressBar progressBar;

  public AppContentView(MainView mainView, CommanderView commanderView) {
    this.mainView = mainView;
    this.commanderView = commanderView;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    content = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainView, commanderView);
    content.setResizeWeight(1);
    content.setDividerLocation(0.7);
    content.setAlignmentX(0);
    add(content);

    breadCrumbs = new BreadCrumbs();
    toolBar = initToolBar();
    toolBar.setAlignmentX(0);
    add(toolBar);

  }

  private JToolBar initToolBar() {
    final var tb = new JToolBar(SwingConstants.HORIZONTAL);
    tb.add(breadCrumbs);
    tb.addSeparator();
    tb.add(Box.createGlue());
    return tb;
  }

  public void showProgressBar(final String displayName) {
    if (progressBar == null) {
      progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
      progressBar.setPreferredSize(new Dimension(300, 20));
    }
    progressBar.setString(displayName);
    progressBar.setStringPainted(true);
    progressBar.setIndeterminate(true);
    toolBar.add(progressBar, 2);
    toolBar.updateUI();
  }

  public void removeProgressBar() {
    if (progressBar == null) {
      return;
    }
    toolBar.remove(progressBar);
    toolBar.updateUI();
  }

  public MainView mainView() {
    return mainView;
  }

  public CommanderView commanderView() {
    return commanderView;
  }
  
  @EventListener
  public void onBreadCrumbsChanged(final BreadCrumbsChanged e) {
    breadCrumbs.set(e.path().getPath());
  }


  private final class BreadCrumbs extends JPanel {

    private final List<BreadCrumbElement> elements = new ArrayList<>();

    private BreadCrumbs() {
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setAlignmentX(LEFT_ALIGNMENT);
    }

    private void set(Object[] path) {
      elements.forEach(this::remove);
      elements.clear();
      if (path == null || path.length < 2) {
        return;
      }

      for (int i = 1; i < path.length; i++) {
        final Object o = path[i];
        if (!(o instanceof DefaultMutableTreeNode node)) {
          continue;
        }

        final var element = new BreadCrumbElement(node, -1, i == 1);
        elements.add(element);
        add(element);
      }
      BreadCrumbs.this.revalidate();
    }

  }


  private final class BreadCrumbElement extends JButton implements ActionListener {
    private static final int SLANT = 10;

    private final DefaultMutableTreeNode node;
    private final boolean leading;


    private BreadCrumbElement(final DefaultMutableTreeNode node,
                              final int textLimit,
                              final boolean leading) {
      this.node = node;
      this.leading = leading;
      setText(MsgStrings.trim(node.toString(), textLimit));
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);

      addActionListener(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
      final var graphics = (Graphics2D) g.create();
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      final int w = getWidth();
      final int h = getHeight();

      final var path = new Path2D.Double();
      path.moveTo(leading ? 0 : SLANT, 0);
      path.lineTo(w, 0);
      path.lineTo(w - SLANT, h);
      path.lineTo(0, h);
      path.closePath();

      graphics.setColor(getBackground());
      graphics.fill(path);

      graphics.setColor(getForeground());
      final var metrics = graphics.getFontMetrics();
      final var text = getText();
      final int textX = (w - metrics.stringWidth(text)) / 2;
      final int textY = (h - metrics.getHeight()) / 2 + metrics.getAscent();
      graphics.drawString(text, textX, textY);

      graphics.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
      final var size = super.getPreferredSize();
      size.width += SLANT;
      return size;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      mainView().mainTreeView().softSelectNode(node);
    }
  }

}
