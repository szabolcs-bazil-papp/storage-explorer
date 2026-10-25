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
  record PipelineStats(List<StageStats> stages) {

    @Override
    public String toString() {
      final var sb = new StringBuilder("Query pipeline stats:\n");
      for (final var stage : stages) {
        sb.append(stage).append("\n");
      }
      return sb.toString();
    }
  }


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
  record StageStats(String stage, long entriesIn, long entriesOut, long timeTaken, long startedAt,
      long endedAt,
      boolean earlyTerminated) {

    @Override
    public String toString() {
      final String fName = String.format("%1$-35s", stage);
      final String entries = String.format("%1$-16s", entriesIn + " -> " + entriesOut);
      final String started = String.format("%1$-16s", formatDuration(startedAt));
      final String finished = String.format("%1$-16s", formatDuration(endedAt));
      final String elapsed = String.format("%1$-16s", formatDuration(timeTaken));
      final String term = String.format("%1$-16s", earlyTerminated);
      return """
          ┌───────────────────────────────────┐
          │%s│
          ├───────────────────────────────────┤
          │Entries          : %s│
          │Started          : %s│
          │Finished         : %s│
          │Time taken       : %s│
          │Early terminated : %s│
          └───────────────────────────────────┘""".formatted(fName, entries, started, finished, elapsed, term);
    }

    public static String formatDuration(long nanos) {
      long hours = nanos / 3_600_000_000_000L;
      nanos %= 3_600_000_000_000L;

      long minutes = nanos / 60_000_000_000L;
      nanos %= 60_000_000_000L;

      long seconds = nanos / 1_000_000_000L;
      nanos %= 1_000_000_000L;

      // 100 µs precision (5 digits after decimal)
      long fractional = nanos / 10_000L;

      return String.format("%02d:%02d:%02d.%05d",
          hours, minutes, seconds, fractional);
    }
  }


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
