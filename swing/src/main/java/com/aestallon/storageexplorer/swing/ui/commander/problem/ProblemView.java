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

package com.aestallon.storageexplorer.swing.ui.commander.problem;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.userconfig.event.ProblemEncountered;
import com.aestallon.storageexplorer.client.userconfig.model.Problem;
import com.aestallon.storageexplorer.client.userconfig.service.ProblemService;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.commander.AbstractCommanderPanelView;
import com.aestallon.storageexplorer.swing.ui.commander.CommanderView;
import com.aestallon.storageexplorer.swing.ui.controller.SideBarController;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;

@Component
public class ProblemView extends AbstractCommanderPanelView implements CommanderView {

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ProblemService problemService;

  private final JTable table;
  private final ProblemTableModel model;

  public ProblemView(UserConfigService userConfigService,
                     SideBarController sideBarController,
                     ProblemService problemService) {
    super(userConfigService, sideBarController);
    this.problemService = problemService;

    setLayout(new BorderLayout());

    this.model = new ProblemTableModel(problemService.problems());
    this.table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    installRenderers(table.getColumnModel());

    add(new JScrollPane(table), BorderLayout.CENTER);
    add(buildToolbar(), BorderLayout.NORTH);
  }

  private void installRenderers(final TableColumnModel columns) {
    // Timestamp renderer
    columns.getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      protected void setValue(Object value) {
        if (value instanceof LocalDateTime ldt) {
          setText(TS_FMT.format(ldt));
        } else {
          super.setValue(value);
        }
      }
    });
    columns.getColumn(0).setPreferredWidth(300);

    // Type with icon
    columns.getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                                                              boolean isSelected, boolean hasFocus,
                                                              int row, int column) {
        final var c =
            (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
                column);
        c.setIcon(iconForType(value instanceof Problem.ProblemType t ? t : null));
        c.setText(value == null ? "" : String.valueOf(value));
        return c;
      }

      private ImageIcon iconForType(final Problem.ProblemType t) {
        if (t == null)
          return null;
        return switch (t) {
          case STORAGE -> IconProvider.DB;
          case ENTRY -> IconProvider.OBJ;
          case GENERIC -> IconProvider.ERROR;
        };
      }
    });
    columns.getColumn(1).setPreferredWidth(300);

    columns.getColumn(2).setPreferredWidth(1_000); // message
  }

  private JToolBar buildToolbar() {
    final var toolbar = new JToolBar();
    toolbar.setFloatable(false);

    final var removeSel = new AbstractAction("Remove", IconProvider.DELETE) {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent e) {
        final int viewRow = table.getSelectedRow();
        if (viewRow < 0)
          return;
        final int modelRow = table.convertRowIndexToModel(viewRow);
        final UUID id = model.problemAt(modelRow).getId();
        problemService.delete(id);
        model.removeAt(modelRow);
      }
    };

    final var clearAll = new AbstractAction("Clear All", IconProvider.DELETE) {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent e) {
        problemService.clear();
        model.setProblems(List.of());
      }
    };

    toolbar.add(clearAll);

    return toolbar;
  }

  @EventListener
  public void onProblemEncountered(final ProblemEncountered event) {
    SwingUtilities.invokeLater(() -> model.add(event.problem()));
  }

  @Override
  public String name() {
    return "Problems";
  }

  @Override
  public ImageIcon icon() {
    return IconProvider.ERROR;
  }

  @Override
  public String tooltip() {
    return "Errors and anomalies encountered while running the software";
  }

  private static final class ProblemTableModel extends AbstractTableModel {

    private final String[] cols = { "Time", "Type", "Message" };
    private final Class<?>[] types =
        { LocalDateTime.class, Problem.ProblemType.class, String.class };

    private transient List<Problem> rows = new ArrayList<>();

    ProblemTableModel(final List<Problem> initial) {
      if (initial != null)
        rows = new ArrayList<>(initial);
    }

    public Problem problemAt(final int modelRow) {
      return rows.get(modelRow);
    }

    public void add(final Problem p) {
      final int idx = rows.size();
      rows.add(p);
      fireTableRowsInserted(idx, idx);
    }

    public void removeAt(final int idx) {
      rows.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    public void setProblems(final List<Problem> ps) {
      rows = new ArrayList<>(Objects.requireNonNull(ps));
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return cols.length;
    }

    @Override
    public String getColumnName(int column) {
      return cols[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return types[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      final Problem p = rows.get(rowIndex);
      return switch (columnIndex) {
        case 0 -> p.getTimestamp();
        case 1 -> p.getType();
        case 2 -> p.getMessage();
        default -> null;
      };
    }
  }
}
