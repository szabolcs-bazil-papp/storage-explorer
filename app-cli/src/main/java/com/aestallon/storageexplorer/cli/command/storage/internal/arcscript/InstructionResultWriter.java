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

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Duration;
import static java.util.stream.Collectors.joining;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.table.BorderStyle;
import org.springframework.shell.table.CellMatchers;
import org.springframework.shell.table.NoWrapSizeConstraints;
import org.springframework.shell.table.TableBuilder;
import org.springframework.shell.table.TableModel;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.client.asexport.ResultSetExporterFactory;

sealed interface InstructionResultWriter {

  void write(ArcScriptResult.InstructionResult result);


  sealed class ConsoleWriter implements InstructionResultWriter {

    protected static final DecimalFormat MS_FORMAT = new DecimalFormat("000.0");

    protected final CommandContext ctx;

    ConsoleWriter(CommandContext ctx) {
      this.ctx = ctx;
    }

    @Override
    public void write(ArcScriptResult.InstructionResult result) {
      switch (result) {
        case ArcScriptResult.IndexingPerformed i -> printIndexingResult(i);
        case ArcScriptResult.QueryPerformed q -> printQueryResult(q);
      }
    }

    private void printIndexingResult(ArcScriptResult.IndexingPerformed indexingPerformed) {
      final var duration = Duration.ofNanos(indexingPerformed.timeTaken());
      final var timeStr = "%ds %sms".formatted(
          duration.getSeconds(),
          MS_FORMAT.format(duration.getNano() / 1_000_000d));
      final var s = "Performed indexing: [ "
                    + indexingPerformed.prettyPrint()
                    + " ] -> "
                    + "Found "
                    + indexingPerformed.entriesFound()
                    + " entries in "
                    + timeStr;
      ctx.getTerminal().writer().println(s);
    }

    protected void printQueryResult(ArcScriptResult.QueryPerformed queryPerformed) {
      final var table = new TableBuilder(QueryResultTableModel.of(queryPerformed.resultSet()))
          .addHeaderAndVerticalsBorders(BorderStyle.oldschool)
          .on(CellMatchers.table()).addSizer(new NoWrapSizeConstraints())
          .build();
      final var terminal = ctx.getTerminal();
      final String res = table.render(terminal.getWidth());

      final var writer = terminal.writer();
      writer.println(res);
    }

  }


  abstract sealed class QueryResultTableModel extends TableModel {

    static QueryResultTableModel of(ArcScriptResult.ResultSet resultSet) {
      return resultSet.meta().columns().isEmpty()
          ? new DefaultQueryResultTableModel(resultSet)
          : new CustomisedQueryResultTableModel(resultSet);
    }

    protected final ArcScriptResult.ResultSet resultSet;

    protected QueryResultTableModel(ArcScriptResult.ResultSet resultSet) {
      this.resultSet = resultSet;
    }

    @Override
    public int getRowCount() {
      return resultSet.size() + 1;
    }

  }


  final class DefaultQueryResultTableModel extends QueryResultTableModel {

    DefaultQueryResultTableModel(ArcScriptResult.ResultSet resultSet) {
      super(resultSet);
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public Object getValue(int row, int column) {
      return row == 0
          ? "URI"
          : resultSet.rows().get(row - 1).entry().uri();
    }
  }


  final class CustomisedQueryResultTableModel extends QueryResultTableModel {

    CustomisedQueryResultTableModel(ArcScriptResult.ResultSet resultSet) {
      super(resultSet);
    }

    @Override
    public int getColumnCount() {
      return resultSet.meta().columns().size();
    }

    @Override
    public Object getValue(int row, int column) {
      if (row == 0) {
        return resultSet.meta().columns().get(column).title();
      } else {
        return resultSet.rows().get(row - 1).cells()
            .get(resultSet.meta().columns().get(column).prop()).displayString();
      }
    }
  }


  final class VerboseConsoleWriter extends ConsoleWriter {
    VerboseConsoleWriter(CommandContext ctx) {
      super(ctx);
    }

    @Override
    protected void printQueryResult(ArcScriptResult.QueryPerformed queryPerformed) {
      final var duration = Duration.ofNanos(queryPerformed.timeTaken());
      final var timeStr = "%ds %sms".formatted(
          duration.getSeconds(),
          MS_FORMAT.format(duration.getNano() / 1_000_000d));
      final var s = "Performed query: [ "
                    + queryPerformed.prettyPrint()
                    + " ] -> "
                    + "Determined "
                    + queryPerformed.resultSet().size()
                    + " matching entries in "
                    + timeStr;
      ctx.getTerminal().writer().println(s);
      final var meta = queryPerformed.resultSet().meta();
      if (!meta.columns().isEmpty()) {
        final var metaaDuration = Duration.ofNanos(meta.timeTaken());
        final var metaTimeStr = "%ds %sms".formatted(
            metaaDuration.getSeconds(),
            MS_FORMAT.format(metaaDuration.getNano() / 1_000_000d));
        final var metaStr = "Rendered columns: [ "
                            + "render "
                            + queryPerformed
                                .resultSet().meta()
                                .columns().stream()
                                .map(it -> "%s as \"%s\"".formatted(it.prop(), it.title()))
                                .collect(joining(","))
                            + " ] -> "
                            + "Fetching properties took "
                            + metaTimeStr;
        ctx.getTerminal().writer().println(metaStr);
      }
      super.printQueryResult(queryPerformed);
    }
  }


  final class FileWriter implements InstructionResultWriter {

    private final Path path;
    private final ResultSetExporterFactory.Target format;

    FileWriter(Path path, ResultSetExporterFactory.Target format) {
      this.path = path;
      this.format = format;
    }

    @Override
    public void write(ArcScriptResult.InstructionResult result) {
      switch (result) {
        case ArcScriptResult.IndexingPerformed i -> {}
        case ArcScriptResult.QueryPerformed q -> new ResultSetExporterFactory()
            .get(format)
            .export(q.resultSet(), path);
      }
    }

  }

}
