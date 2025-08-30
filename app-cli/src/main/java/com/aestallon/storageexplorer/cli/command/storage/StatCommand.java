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

package com.aestallon.storageexplorer.cli.command.storage;

import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.CommandAvailability;
import org.springframework.shell.table.BorderStyle;
import org.springframework.shell.table.CellMatchers;
import org.springframework.shell.table.NoWrapSizeConstraints;
import org.springframework.shell.table.TableBuilder;
import org.springframework.shell.table.TableModel;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.cli.command.CommandConstants;
import com.aestallon.storageexplorer.cli.service.StorageInstanceContext;
import com.aestallon.storageexplorer.client.storage.stat.SchemaStats;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

@Component
@Command(
    command = "stat",
    description = "Displays statistics about the selected storage instance.",
    group = CommandConstants.COMMAND_GROUP_STORAGE)
public class StatCommand {


  private final StorageInstanceContext storageInstanceContext;

  public StatCommand(StorageInstanceContext storageInstanceContext) {
    this.storageInstanceContext = storageInstanceContext;
  }

  @Command(command = "schema", description = "Displays schema information.")
  @CommandAvailability(provider = CommandConstants.REQUIRES_STORAGE)
  public void statSchema(CommandContext ctx) {
    final var storageInstance = storageInstanceContext
        .current()
        .orElseThrow(() -> new IllegalStateException(
            "No storage instance selected! Use the 'use' command to select one!"));

    final var table = new TableBuilder(new SchemaStatTableModel(storageInstance))
        .addHeaderAndVerticalsBorders(BorderStyle.oldschool)
        .on(CellMatchers.table()).addSizer(new NoWrapSizeConstraints())
        .build();
    final var terminal = ctx.getTerminal();
    final String res = table.render(terminal.getWidth());

    final var writer = terminal.writer();
    writer.println(res);
    writer.flush();
  }


  private static final class SchemaStatTableModel extends TableModel {

    private final SchemaStats.SchemaStatData data;

    private SchemaStatTableModel(StorageInstance storageInstance) {
      this.data = SchemaStats.of(storageInstance);
    }

    @Override
    public int getRowCount() {
      return data.getRowCount() + 1;
    }

    @Override
    public int getColumnCount() {
      return data.getColumnCount();
    }

    @Override
    public Object getValue(int row, int column) {
      return row == 0 ? data.getHeader(column) : data.getValueAt(row - 1, column);
    }

  }

}
