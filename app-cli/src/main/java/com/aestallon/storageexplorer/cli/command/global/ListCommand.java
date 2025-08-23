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

package com.aestallon.storageexplorer.cli.command.global;

import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.table.BorderStyle;
import org.springframework.shell.table.CellMatchers;
import org.springframework.shell.table.NoWrapSizeConstraints;
import org.springframework.shell.table.Table;
import org.springframework.shell.table.TableBuilder;
import org.springframework.shell.table.TableModel;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.cli.command.CommandConstants;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;

@Component
@Command
public class ListCommand {

  private final StorageInstanceProvider storageInstanceProvider;

  public ListCommand(StorageInstanceProvider storageInstanceProvider) {
    this.storageInstanceProvider = storageInstanceProvider;
  }

  @Command(
      command = "list",
      alias = "ls",
      description = "List all available storages",
      group = CommandConstants.COMMAND_GROUP_UNCONDITIONAL)
  public Table list() {
    final var model = new StorageInstanceTableModel(storageInstanceProvider.provide().toList());
    return new TableBuilder(model)
        .addHeaderAndVerticalsBorders(BorderStyle.oldschool)
        .on(CellMatchers.table()).addSizer(new NoWrapSizeConstraints())
        .build();
  }

  private static final class StorageInstanceTableModel extends TableModel {

    private final java.util.List<StorageInstance> storageInstances;

    private StorageInstanceTableModel(java.util.List<StorageInstance> storageInstances) {
      this.storageInstances = storageInstances;
    }

    @Override
    public int getRowCount() {
      return storageInstances.size() + 1;
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public Object getValue(int row, int column) {
      return switch (row) {

        case 0 -> switch (column) {
          case 0 -> "Name";
          case 1 -> "Type";
          case 2 -> "Location";
          default -> "UNKNOWN";
        };

        default -> {
          final var storageInstance = storageInstances.get(row - 1);
          yield switch (column) {
            case 0 -> storageInstance.name();

            case 1 -> switch (storageInstance.type()) {
              case FS -> "File System";
              case DB -> switch (storageInstance.location()) {
                case SqlStorageLocation sql -> String.valueOf(sql.getVendor());
                default -> "Something is horribly wrong!";
              };
            };

            case 2 -> switch (storageInstance.location()) {
              case SqlStorageLocation sql -> {
                if (sql.getDbConnectionData() == null) {
                  yield "Unknown";
                }

                final var url = sql.getDbConnectionData().getUrl();
                final var targetSchema = sql.getDbConnectionData().getTargetSchema();
                if (targetSchema != null) {
                  yield "%s (%s)".formatted(url, targetSchema);
                }
                yield url;
              }
              case FsStorageLocation fs -> String.valueOf(fs.getPath());
            };

            default -> "UNKNOWN";
          };
        }

      };
    }
  }

}
