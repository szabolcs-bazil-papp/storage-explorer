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

import java.util.List;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;

sealed abstract class IntermediaryResultProcessor {

  static IntermediaryResultProcessor of(ArcScriptContext arcScriptContext) {
    return arcScriptContext.verbose() ?
        new ConsoleWriter(arcScriptContext) :
        new NoOp(arcScriptContext);
  }

  protected final ArcScriptContext ctx;

  protected IntermediaryResultProcessor(ArcScriptContext arcScriptContext) {
    this.ctx = arcScriptContext;
  }

  abstract void process(List<ArcScriptResult.InstructionResult> elements);


  private static final class NoOp extends IntermediaryResultProcessor {

    private NoOp(ArcScriptContext arcScriptContext) {
      super(arcScriptContext);
    }

    @Override
    void process(List<ArcScriptResult.InstructionResult> elements) {}

  }


  private static final class ConsoleWriter extends IntermediaryResultProcessor {

    private ConsoleWriter(ArcScriptContext arcScriptContext) {
      super(arcScriptContext);
    }

    @Override
    void process(List<ArcScriptResult.InstructionResult> elements) {
      final var writer = new InstructionResultWriter.VerboseConsoleWriter(ctx.commandContext());
      elements.forEach(writer::write);
    }

  }


}
