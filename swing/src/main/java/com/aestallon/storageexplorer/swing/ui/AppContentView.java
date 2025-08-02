package com.aestallon.storageexplorer.swing.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.graph.event.GraphState;
import com.aestallon.storageexplorer.client.userconfig.event.StorageEntryUserDataChanged;
import com.aestallon.storageexplorer.common.util.MsgStrings;
import com.aestallon.storageexplorer.core.event.LoadingQueueSize;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.swing.ui.commander.CommanderView;
import com.aestallon.storageexplorer.swing.ui.event.BreadCrumbsChanged;
import com.aestallon.storageexplorer.swing.ui.misc.HiddenPaneSize;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;

@Component
public class AppContentView extends JPanel {

  private final MainView mainView;
  private final CommanderView commanderView;
  private final JSplitPane content;
  private final JToolBar toolBar;
  private final BreadCrumbs breadCrumbs;
  private final GraphStateLabel graphStateLabel;
  private final LoadingQueueLabel loadingQueueLabel;
  private JProgressBar progressBar;
  private HiddenPaneSize hiddenPaneSize;

  public AppContentView(MainView mainView, CommanderView commanderView) {
    this.mainView = mainView;
    this.commanderView = commanderView;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    /* Inner content stuff */
    final var inner = new JPanel();
    inner.setAlignmentX(LEFT_ALIGNMENT);
    inner.setLayout(new BoxLayout(inner, BoxLayout.X_AXIS));
    inner.add(new SideBar());

    content = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainView, null);
    content.setResizeWeight(1);
    content.setDividerSize(0);
    content.setAlignmentX(LEFT_ALIGNMENT);
    inner.add(content);
    add(inner);

    /* Toolbar stuff */
    breadCrumbs = new BreadCrumbs();
    graphStateLabel = new GraphStateLabel();
    loadingQueueLabel = new LoadingQueueLabel();
    toolBar = initToolBar();
    add(toolBar);

    hiddenPaneSize = new HiddenPaneSize(-1, -1, 500, 5);
  }

  private JToolBar initToolBar() {
    final var tb = new JToolBar(SwingConstants.HORIZONTAL);
    tb.setAlignmentX(LEFT_ALIGNMENT);
    tb.add(breadCrumbs);
    tb.addSeparator();
    tb.add(Box.createGlue());
    tb.add(graphStateLabel);
    tb.add(loadingQueueLabel);
    return tb;
  }

  private boolean showingCommander() {
    return hiddenPaneSize == null;
  }

  public void showHideCommander(final boolean show) {
    if (show == showingCommander()) {
      return;
    }

    if (show) {
      if (content.getRightComponent() == null) {
        content.setRightComponent(commanderView);
      }

      content.setDividerLocation(hiddenPaneSize.dividerLocation());
      content.setDividerSize(hiddenPaneSize.dividerSize());
      commanderView.setMinimumSize(null);
      commanderView.setMaximumSize(null);
      commanderView.setPreferredSize(hiddenPaneSize.toPreferredSize());

      hiddenPaneSize = null;
    } else {
      final Dimension preferredSize = commanderView.getPreferredSize();
      final int dividerLocation = content.getDividerLocation();
      final int dividerSize = content.getDividerSize();
      hiddenPaneSize = HiddenPaneSize.of(preferredSize, dividerLocation, dividerSize);

      content.setDividerLocation(1.0);
      content.setDividerSize(0);
      commanderView.setMinimumSize(new Dimension(0, 0));
      commanderView.setMaximumSize(new Dimension(0, 0));
      content.setRightComponent(null);
    }
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
  
  public void setLoadingQueueSize(final LoadingQueueSize event) {
    loadingQueueLabel.setNumber(event);
  }

  @EventListener
  public void onBreadCrumbsChanged(final BreadCrumbsChanged e) {
    breadCrumbs.set(e.path().getPath());
  }
  
  @EventListener
  public void onStorageEntryUserDataChanged(final StorageEntryUserDataChanged event) {
    SwingUtilities.invokeLater(() -> {
      breadCrumbs.elements.forEach(BreadCrumbElement::setText);
    });
  }
  
  public void setGraphState(final GraphState state) {
    graphStateLabel.setState(state);
  }
  
  private static final class LoadingQueueLabel extends JLabel {
    private final Map<StorageId, Long> sizes;
    public LoadingQueueLabel() {
      sizes = new HashMap<>();
      setNumberInternal(0L);
      setOpaque(true);
      setHorizontalAlignment(SwingConstants.CENTER);
      setFont(getFont().deriveFont(Font.BOLD));
      setToolTipText("The number of entries waiting to be loaded. The application may become temporarily unresponsive if this number is greater than 0.");
      setBorder(new EmptyBorder(2, 15, 2, 15));
    }

    public void setNumber(LoadingQueueSize size) {
      sizes.put(size.storageId(), size.size());
      setNumberInternal(sizes.values().stream().reduce(0L, Long::sum));
    }
    
    private void setNumberInternal(final long number) {
      setText(String.valueOf(number));

      if (number < 1000) {
        setBackground(new Color(205, 250, 167));
      } else if (number < 5000) {
        setBackground(new Color(255, 209, 120));
      } else {
        setBackground(new Color(244, 121, 113));
      }
    }
  }
  
  private static final class GraphStateLabel extends JLabel {
    private long nodes;
    private long edges;
    
    private GraphStateLabel() {
      setHorizontalAlignment(SwingConstants.CENTER);
      setBorder(new EmptyBorder(2, 15, 2, 15));
      setTextInternal();
    }
    
    private void setTextInternal() {
      if (nodes < 1L && edges < 1L) {
        setText("");
        setOpaque(false);
      } else {
        setText("Nodes: " + nodes + " Edges: " + edges);
        setOpaque(true);
      }
    }
    
    private void setState(GraphState state) {
      nodes = state.nodeCount();
      edges = state.edgeCount();
      setTextInternal();
    }
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
    private final int textLimit;
    private final boolean leading;

    private BreadCrumbElement(final DefaultMutableTreeNode node,
                              final int textLimit,
                              final boolean leading) {
      this.node = node;
      this.textLimit = textLimit;
      this.leading = leading;
      setText();
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);

      addActionListener(this);
    }
    
    private void setText() {
      setText(MsgStrings.trim(node.toString(), textLimit));
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
      mainView().mainTreeView().selectNodeSoft(node);
    }
  }


  private final class SideBar extends JPanel {
    private static final int MIN_WIDTH = 30;

    private SideBar() {
      setMinimumSize(new Dimension(MIN_WIDTH, 60));
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setAlignmentY(TOP_ALIGNMENT);

      final var showHideTree = new JToggleButton(IconProvider.TREE);
      showHideTree.setToolTipText("Show/hide tree displaying storage hierarchy");
      showHideTree.setFocusPainted(false);
      showHideTree.setSelected(true);
      
      add(showHideTree);
      
      final var showHideAs = new JToggleButton(IconProvider.ARC_SCRIPT);
      showHideAs.setToolTipText("Show/hide scripting facilities");
      showHideAs.setFocusPainted(false);
      showHideAs.setSelected(false);
      showHideTree.addActionListener(e -> {
        showHideAs.setSelected(false);
        mainView().showHideTree(showHideTree.isSelected());
      });
      showHideAs.addActionListener(e -> {
        showHideTree.setSelected(false);
        mainView().showHideAs(showHideAs.isSelected());
      });
      
      add(Box.createVerticalGlue());

      final var showHideCommander = new JToggleButton(IconProvider.TERMINAL);
      showHideCommander.setToolTipText("Show/hide scripting facilities");
      showHideCommander.setFocusPainted(false);
      showHideCommander.addActionListener(e -> showHideCommander(showHideCommander.isSelected()));
      add(showHideCommander);
    }

  }

}
