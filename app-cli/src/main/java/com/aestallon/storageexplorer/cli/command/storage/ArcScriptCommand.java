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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.CommandAvailability;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.cli.command.CommandConstants;
import com.aestallon.storageexplorer.cli.command.storage.internal.arcscript.ArcScriptContext;
import com.aestallon.storageexplorer.cli.command.storage.internal.arcscript.ScriptProcessingStrategy;
import com.aestallon.storageexplorer.cli.service.StorageInstanceContext;
import com.aestallon.storageexplorer.client.asexport.ResultSetExporterFactory;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.google.common.base.Strings;

@Component
@Command(
    command = "script",
    description = "Execute an ArcScript query",
    group = CommandConstants.COMMAND_GROUP_STORAGE)
public class ArcScriptCommand {

  private final StorageInstanceContext storageInstanceContext;

  public ArcScriptCommand(StorageInstanceContext storageInstanceContext) {
    this.storageInstanceContext = storageInstanceContext;
  }

  @Command(command = "file", description = "Execute an ArcScript query from a file.")
  @CommandAvailability(provider = CommandConstants.REQUIRES_STORAGE)
  public void runScript(CommandContext ctx,
                        @Option(
                            longNames = "file",
                            shortNames = 'f',
                            required = true,
                            arity = CommandRegistration.OptionArity.EXACTLY_ONE,
                            arityMin = 1,
                            arityMax = 1,
                            description = "The absolute or relative path to the script file.",
                            label = "File") Path path,
                        @Option(longNames = "verbose",
                            shortNames = 'v',
                            required = false,
                            arity = CommandRegistration.OptionArity.ZERO_OR_ONE,
                            arityMin = 0,
                            arityMax = 1,
                            description = "Whether to print intermediary steps.",
                            label = "Verbose mode",
                            defaultValue = "false") boolean verbose,
                        @Option(longNames = "output",
                            shortNames = 'o',
                            required = false,
                            arity = CommandRegistration.OptionArity.ZERO_OR_ONE,
                            arityMin = 0,
                            arityMax = 1,
                            description = "The file to write the query results.",
                            label = "Output File") String output,
                        @Option(longNames = "format",
                            shortNames = 'f',
                            required = false,
                            arity = CommandRegistration.OptionArity.ZERO_OR_ONE,
                            arityMin = 0,
                            arityMax = 1,
                            description = "The format in which in the output file should be written (CSV or JSON).",
                            label = "Output File Format") String format) {
    if (path == null || !Files.exists(path)) {
      throw new IllegalArgumentException("File does not exist: [%s]".formatted(path));
    }

    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("File is a directory: [%s]".formatted(path));
    }

    try {
      final String script = Files.readString(path);
      runScriptInline(ctx, script, verbose, output, format);
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not read script file: [%s]".formatted(path), e);
    }
  }

  @Command(
      command = "inline",
      description = "Execute an ArcScript query by providing the entire script as an argument")
  @CommandAvailability(provider = CommandConstants.REQUIRES_STORAGE)
  public void runScriptInline(CommandContext ctx,
                              @Option(longNames = "script",
                                  shortNames = 's',
                                  required = true,
                                  arity = CommandRegistration.OptionArity.EXACTLY_ONE,
                                  arityMin = 1,
                                  arityMax = 1,
                                  description = "The ArcScript query to execute.",
                                  label = "Script") String script,
                              @Option(longNames = "verbose",
                                  shortNames = 'v',
                                  required = false,
                                  arity = CommandRegistration.OptionArity.ZERO_OR_ONE,
                                  arityMin = 0,
                                  arityMax = 1,
                                  description = "Whether to print intermediary steps.",
                                  label = "Verbose mode",
                                  defaultValue = "false") boolean verbose,
                              @Option(longNames = "output",
                                  shortNames = 'o',
                                  required = false,
                                  arity = CommandRegistration.OptionArity.ZERO_OR_ONE,
                                  arityMin = 0,
                                  arityMax = 1,
                                  description = "The file to write the query results.",
                                  label = "Output File") String output,
                              @Option(longNames = "format",
                                  shortNames = 'f',
                                  required = false,
                                  arity = CommandRegistration.OptionArity.ZERO_OR_ONE,
                                  arityMin = 0,
                                  arityMax = 1,
                                  description = "The format in which in the output file should be written (CSV or JSON).",
                                  label = "Output File Format") String format) {
    if (Strings.isNullOrEmpty(script)) {
      throw new IllegalArgumentException("Script must not be empty or blank.");
    }

    final StorageInstance storageInstance = storageInstanceContext
        .current()
        .orElseThrow(() -> new IllegalStateException(
            "No storage instance selected! Use the 'use' command to select one!"));

    final ResultSetExporterFactory.Target explicitFormat;
    if (Strings.isNullOrEmpty(format)) {
      explicitFormat = null;
    } else if ("csv".equalsIgnoreCase(format)) {
      explicitFormat = ResultSetExporterFactory.Target.CSV;
    } else if ("json".equalsIgnoreCase(format)) {
      explicitFormat = ResultSetExporterFactory.Target.JSON;
    } else {
      throw new IllegalArgumentException("Unsupported output format: %s".formatted(format));
    }

    final Path path = Strings.isNullOrEmpty(output) ? null : Path.of(output).normalize();
    ScriptProcessingStrategy
        .of(new ArcScriptContext(ctx, script, storageInstance, verbose, path, explicitFormat))
        .process();
  }

}
