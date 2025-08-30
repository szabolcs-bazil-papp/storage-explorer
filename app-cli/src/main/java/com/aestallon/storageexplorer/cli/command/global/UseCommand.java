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

import org.springframework.shell.command.CommandRegistration;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.cli.command.CommandConstants;
import com.aestallon.storageexplorer.cli.service.StorageInstanceContext;

@Component
@Command
public class UseCommand {

  private final StorageInstanceContext storageInstanceContext;

  public UseCommand(StorageInstanceContext storageInstanceContext) {
    this.storageInstanceContext = storageInstanceContext;
  }

  @Command(
      command = "use",
      description = "Set the curent execution context to a given storage.",
      group = CommandConstants.COMMAND_GROUP_UNCONDITIONAL)
  public void use(@Option(
      longNames = "name",
      shortNames = 'n',
      required = true,
      arity = CommandRegistration.OptionArity.EXACTLY_ONE) String name) {
    final var storageInstance = storageInstanceContext.storageInstanceProvider().provide()
        .filter(it -> it.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Storage instance not found."));
    storageInstanceContext.current(storageInstance);
  }

}
