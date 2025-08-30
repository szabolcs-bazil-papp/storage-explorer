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
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;

public final class ScriptProcessingStrategy {

  public static ScriptProcessingStrategy of(final ArcScriptContext ctx) {
    final var intermediaryResultProcessor = IntermediaryResultProcessor.of(ctx);
    final var finalResultProcessor = FinalResultProcessor.of(ctx);
    return new ScriptProcessingStrategy(ctx, intermediaryResultProcessor, finalResultProcessor);
  }



  private final ArcScriptContext ctx;
  private final IntermediaryResultProcessor intermediaryResultProcessor;
  private final FinalResultProcessor finalResultProcessor;

  private ScriptProcessingStrategy(ArcScriptContext arcScriptContext,
                                   IntermediaryResultProcessor intermediaryResultProcessor,
                                   FinalResultProcessor finalResultProcessor) {
    this.ctx = arcScriptContext;
    this.intermediaryResultProcessor = intermediaryResultProcessor;
    this.finalResultProcessor = finalResultProcessor;
  }

  public void process() {
    switch (Arc.evaluate(ctx.script(), ctx.storageInstance())) {
      case ArcScriptResult.Ok(var results) -> processResults(results);
      case ArcScriptResult.CompilationError compErr -> printCompilationError(compErr);
      case ArcScriptResult.ImpermissibleInstruction(String msg, String cause) ->
          throw new IllegalArgumentException(msg + cause);
      case ArcScriptResult.UnknownError(String msg) ->
          throw new IllegalStateException("Unknown error occurred: " + msg);
    }
  }


  private void processResults(List<ArcScriptResult.InstructionResult> elements) {
    final var intermediaries = elements.subList(0, elements.size() -1);
    final var last = elements.getLast();
    
    intermediaryResultProcessor.process(intermediaries);
    finalResultProcessor.process(last);
    ctx.commandContext().getTerminal().writer().flush();
  }

  private void printCompilationError(ArcScriptResult.CompilationError compErr) {
    throw new IllegalArgumentException(
        "Compilation Error occurred at line %d, column %d: %s".formatted(
            compErr.line(),
            compErr.col(),
            compErr.msg()));
  }

}
