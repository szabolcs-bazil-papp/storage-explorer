package com.aestallon.storageexplorer.arcscript.engine;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.aestallon.storageexplorer.arcscript.internal.Instruction;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public sealed interface ArcScriptResult {

  String ERR_MARKER = "line (\\d+), column (\\d+)";
  Pattern ERR_PATTERN = Pattern.compile(ERR_MARKER);

  static ArcScriptResult err(final Exception e) {
    String message = e.getMessage();
    Matcher m = ERR_PATTERN.matcher(message);
    if (m.find()) {
      final String lineStr = m.group(1);
      final String colStr = m.group(2);
      try {
        return new CompilationError(Integer.parseInt(lineStr), Integer.parseInt(colStr), message);
      } catch (NumberFormatException nfe) {
        return new UnknownError(message);
      }
    }

    for (final var ste : e.getStackTrace()) {
      if (ste.getFileName() != null && ste.getFileName().contains(".groovy")) {
        return new CompilationError(ste.getLineNumber(), 0, message);
      }

      if (ste.getClassName().contains("Script")) {
        return new CompilationError(ste.getLineNumber(), 0, message);
      }
    }

    return new UnknownError(message);
  }

  static ArcScriptResult ok(final List<ActionElement> elements) {
    return new Ok(elements);
  }

  static ArcScriptResult empty() {
    return new Ok(Collections.emptyList());
  }
  
  static ArcScriptResult impermissible(String message, Instruction instruction) {
    return new ImpermissibleInstruction(message, instruction.toString());
  }

  record CompilationError(int line, int col, String msg) implements ArcScriptResult {}


  record UnknownError(String msg) implements ArcScriptResult {}

  record ImpermissibleInstruction(String msg, String prettyPrint) implements ArcScriptResult {}
  
  sealed interface ActionElement {}


  record IndexingPerformed(boolean implicit,
      Set<String> schemae, Set<String> types,
      String prettyPrint, long entriesFound)
      implements ActionElement {}


  record QueryPerformed(String prettyPrint, Set<StorageEntry> resultSet) implements ActionElement {}


  record Ok(List<ActionElement> elements) implements ArcScriptResult {}

}
