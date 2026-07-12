package com.aestallon.storageexplorer.arcscript.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    }

    return new UnknownError(message);
  }

  static ArcScriptResult ok(final List<InstructionResult> elements) {
    return ok(elements, false);
  }

  static ArcScriptResult ok(final List<InstructionResult> elements, boolean verbose) {
    return new Ok(elements, verbose);
  }

  static ArcScriptResult empty() {
    return ok(Collections.emptyList());
  }

  static ArcScriptResult impermissible(String message, Instruction instruction) {
    return new ImpermissibleInstruction(message, instruction.toString());
  }

  record CompilationError(int line, int col, String msg) implements ArcScriptResult {}


  record UnknownError(String msg) implements ArcScriptResult {}


  record ImpermissibleInstruction(String msg, String prettyPrint) implements ArcScriptResult {}


  sealed interface InstructionResult {}


  record IndexingPerformed(boolean implicit,
      Set<String> schemae, Set<String> types,
      String prettyPrint, long entriesFound, long timeTaken)
      implements InstructionResult {}


  record QueryPerformed(String prettyPrint, ResultSet resultSet, long timeTaken,
      PipelineStats pipelineStats)
      implements InstructionResult {

    public QueryPerformed(String prettyPrint, ResultSet resultSet, long timeTaken) {
      this(prettyPrint, resultSet, timeTaken, null);
    }

  }


  /**
   * Per-stage instrumentation of a pipelined query execution.
   *
   * <p>
   * Only attached to a {@link QueryPerformed} result when the executing engine is pipeline-based
   * and stage timing collection is enabled; {@code null} otherwise.
   */
  record PipelineStats(List<StageStats> stages) {}


  /**
   * Instrumentation of a single pipeline stage.
   *
   * @param stage the name of the pipeline stage
   * @param entriesIn number of elements the stage received from its upstream
   * @param entriesOut number of elements the stage emitted downstream
   * @param timeTaken nanos elapsed from the stage's first processed element until the stage
   *     completed; {@code -1} if the stage never received an element
   * @param earlyTerminated whether the stage stopped exhausting its input before completion:
   *     either by cancelling its upstream (limit satisfied), or by discarding entries under a
   *     configured hard cap
   */
  record StageStats(String stage, long entriesIn, long entriesOut, long timeTaken,
      boolean earlyTerminated) {}


  record ColumnDescriptor(String prop, String title) {}


  record ResultSetMeta(List<ColumnDescriptor> columns, long timeTaken) {}


  enum CellType { SIMPLE, COMPLEX }


  record DataCell(CellType type, Object value) {

    static DataCell noValue() {
      return new DataCell(CellType.SIMPLE, null);
    }

    static DataCell of(Object value) {
      return new DataCell(CellType.COMPLEX, value);
    }

    @Override
    public String toString() {
      return value == null ? "" : value.toString();
    }

    public String displayString() {
      return switch (value) {
        case null -> "";
        case String s -> "\"" + s + "\"";
        default -> value.toString();
      };
    }

  }


  record QueryResultRow(StorageEntry entry, Map<String, DataCell> cells) {

    public QueryResultRow(StorageEntry entry) {
      this(entry, Collections.emptyMap());
    }

  }


  record ResultSet(ResultSetMeta meta, List<QueryResultRow> rows) {

    public int size() {
      return rows.size();
    }

    public List<StorageEntry> entries() {
      return rows.stream().map(QueryResultRow::entry).collect(Collectors.toList());
    }

  }


  record Ok(List<InstructionResult> elements, boolean verbose) implements ArcScriptResult {}

}
