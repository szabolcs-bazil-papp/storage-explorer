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

package com.aestallon.storageexplorer.cli.command.storage.internal.arcscript;

import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.client.asexport.ResultSetExporterFactory;

final class FinalResultProcessor {

  static FinalResultProcessor of(ArcScriptContext ctx) {
    final InstructionResultWriter writer;
    final var output = ctx.output();
    if (output == null) {
      writer = ctx.verbose()
          ? new InstructionResultWriter.VerboseConsoleWriter(ctx.commandContext())
          : new InstructionResultWriter.ConsoleWriter(ctx.commandContext());
    } else {
      var target = ctx.format();
      if (target == null) {
        if (output.getFileName().toString().toLowerCase().endsWith(".json")) {
          target = ResultSetExporterFactory.Target.JSON;
        } else if (output.getFileName().toString().toLowerCase().endsWith(".csv")) {
          target = ResultSetExporterFactory.Target.CSV;
        } else {
          throw new IllegalArgumentException(
              "Unsupported output format: %s".formatted(output.getFileName()));
        }
      }
      writer = new InstructionResultWriter.FileWriter(output, target);
    }

    return new FinalResultProcessor(writer);
  }

  private final InstructionResultWriter writer;

  private FinalResultProcessor(InstructionResultWriter writer) {
    this.writer = writer;
  }

  void process(ArcScriptResult.InstructionResult result) {
    writer.write(result);
  }

}
