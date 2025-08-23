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


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.component.flow.ResultMode;
import org.springframework.shell.component.flow.SelectItem;
import org.springframework.shell.component.support.Itemable;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.cli.command.CommandConstants;
import com.aestallon.storageexplorer.cli.service.StorageInstanceContext;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.Availability;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseConnectionData;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseVendor;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;
import com.google.common.base.Strings;

@Component
@Command(
    command = "import",
    description = "Import a storage instance",
    group = CommandConstants.COMMAND_GROUP_UNCONDITIONAL)
public class ImportCommand {

  private final StorageInstanceContext storageInstanceContext;
  private final ComponentFlow.Builder flowBuilder;

  public ImportCommand(StorageInstanceContext storageInstanceContext,
                       ComponentFlow.Builder flowBuilder) {
    this.storageInstanceContext = storageInstanceContext;
    this.flowBuilder = flowBuilder;
  }

  @Command(command = "fs", description = "Import a file system storage.")
  public void fsImport(@Option(longNames = "name", shortNames = 'n', required = true) String name,
                       @Option(longNames = "path", shortNames = 'p', required = true) String path) {
    if (Strings.isNullOrEmpty(name) || name.isBlank()) {
      throw new IllegalArgumentException("Name must not be empty or blank.");
    }

    final Path p = Path.of(path).toAbsolutePath();
    if (!Files.exists(p) || !Files.isDirectory(p)) {
      throw new IllegalArgumentException("Path must be a valid directory.");
    }

    final StorageInstanceDto dto = new StorageInstanceDto()
        .id(UUID.randomUUID())
        .name(name)
        .availability(Availability.AVAILABLE)
        .indexingStrategy(IndexingStrategyType.ON_DEMAND)
        .type(StorageInstanceType.FS)
        .fs(new FsStorageLocation().path(p));
    final var storageInstance = StorageInstance.fromDto(dto);
    storageInstanceContext.storageInstanceProvider().importAndIndex(storageInstance);
    storageInstanceContext.current(storageInstance);
  }

  @Command(command = "db", description = "Import a relational database storage.")
  public void dbImport(@Option(longNames = "name", shortNames = 'n', required = true) String name,
                       @Option(longNames = "url", shortNames = 'u', required = true) String url,
                       @Option(longNames = "username", required = true) String username,
                       @Option(longNames = "password", required = true) String password,
                       @Option(longNames = "schema", shortNames = 's') String targetSchema) {
    if (Strings.isNullOrEmpty(name) || name.isBlank()) {
      throw new IllegalArgumentException("Name must not be empty or blank.");
    }

    if (Strings.isNullOrEmpty(url) || url.isBlank()) {
      throw new IllegalArgumentException("JDBC URL must not be empty or blank.");
    }
    
    if (Strings.isNullOrEmpty(username) || username.isBlank()) {
      throw new IllegalArgumentException("Username must not be empty or blank.");
    }
    
    if (Strings.isNullOrEmpty(password) || password.isBlank()) {
      throw new IllegalArgumentException("Password must not be empty or blank.");
    }
    
    final StorageInstanceDto dto = new StorageInstanceDto()
        .id(UUID.randomUUID())
        .name(name)
        .indexingStrategy(IndexingStrategyType.ON_DEMAND)
        .type(StorageInstanceType.DB)
        .db(new SqlStorageLocation()
            .vendor(url.contains("postgres") ? DatabaseVendor.PG : DatabaseVendor.ORACLE)
            .dbConnectionData(new DatabaseConnectionData()
                .username(username)
                .password(password)
                .url(url)
                .targetSchema(targetSchema)));
    final var storageInstance = StorageInstance.fromDto(dto);
    storageInstanceContext.storageInstanceProvider().importAndIndex(storageInstance);
    storageInstanceContext.current(storageInstance);
  }

  @Command(command = "flow", description = "Import a new storage using a wizard.", hidden = true)
  public void flowImport(CommandContext context) {
    final var flow = flowBuilder.clone().reset()

        .withStringInput("_name")
        .name("Name")
        .resultMode(ResultMode.VERIFY)
        .preHandler(ctx -> {
          if (ctx.getDefaultValue() != null && ctx.getDefaultValue().isBlank()) {
            ctx.setDefaultValue(null);
          }

          if (ctx.getResultValue() != null && ctx.getResultValue().isBlank()) {
            ctx.setResultValue(null);
          }
        })
        .and()

        .withSingleItemSelector("_type")
        .name("Type")
        .selectItems(Arrays.stream(StorageInstanceType.values())
            .map(it -> SelectItem.of(it.name(), it.name()))
            .toList())
        .next(ctx -> ctx.getResultItem().map(Itemable::getItem)
            .filter(it -> StorageInstanceType.DB.name().equals(it)).map(it -> "_db_url")
            .orElse("_fs_path"))
        .and()

        .withPathInput("_fs_path")
        .name("Location")
        .next(ctx -> null)
        .and()

        .withStringInput("_db_url")
        .name("JDBC URL")
        .next(ctx -> null)
        .and()

        .build();
    ComponentFlow.ComponentFlowResult result = flow.run();
    final var name = result.getContext().get("_name", String.class);
    final var type = result.getContext().get("_type", String.class);
    final var location = result.getContext().get("_fs_path", String.class);
    final var jdbcUrl = result.getContext().get("_db_url", String.class);
    final var writer = context.getTerminal().writer();
    writer.println(
        "Importing storage '%s' of type '%s' at '%s' or '%s'".formatted(name, type, location,
            jdbcUrl));
    writer.flush();
  }
}
