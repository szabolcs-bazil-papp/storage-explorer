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
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.cli.command.CommandConstants;
import com.aestallon.storageexplorer.cli.service.StorageInstanceContext;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.index.impl.TreeBookmarkIndex;

@Component
@Command
public class IndexCommand {

  private final StorageInstanceContext storageInstanceContext;

  public IndexCommand(StorageInstanceContext storageInstanceContext) {
    this.storageInstanceContext = storageInstanceContext;
  }

  @Command(
      command = "index",
      description = "Indexes the object storage.",
      group = CommandConstants.COMMAND_GROUP_STORAGE)
  @CommandAvailability(provider = CommandConstants.REQUIRES_STORAGE)
  public void index(CommandContext ctx) {
    final var treeIndex = storageInstanceContext.current()
        .orElseThrow(() -> new IllegalStateException(
            "No storage instance selected! Use the 'use' command to select one!"))
        .treeIndex();
    final var writer = ctx.getTerminal().writer();
    switch (treeIndex.rebuildSurface(IndexingTarget.any())) {
      case TreeBookmarkIndex.SurfaceRebuildResult.Completed(int count) ->
          writer.println("Indexed " + count + " entries.");
      case TreeBookmarkIndex.SurfaceRebuildResult.Aborted(String reason) ->
          writer.println("Indexing aborted: " + reason);
    }
    writer.flush();
  }

}
