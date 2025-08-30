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
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.cli.command.CommandConstants;
import com.aestallon.storageexplorer.cli.service.StorageInstanceContext;

@Component
@Command
public class DropCommand {

  private final StorageInstanceContext storageInstanceContext;

  public DropCommand(StorageInstanceContext storageInstanceContext) {
    this.storageInstanceContext = storageInstanceContext;
  }

  @Command(
      command = "drop",
      description = "Drop a storage instance.",
      group = CommandConstants.COMMAND_GROUP_UNCONDITIONAL)
  public void drop(@Option(longNames = "name", shortNames = 'n', required = true) String name) {
    final var storageInstanceProvider = storageInstanceContext.storageInstanceProvider();
    final var instancesToRemove = storageInstanceProvider.provide()
        .filter(it -> name.equals(it.name()))
        .toList();
    storageInstanceContext.current().ifPresent(it -> {
      if (instancesToRemove.contains(it)) {
        storageInstanceContext.current(null);
      }
    });
    instancesToRemove.forEach(storageInstanceProvider::discardIndex);
  }

}
