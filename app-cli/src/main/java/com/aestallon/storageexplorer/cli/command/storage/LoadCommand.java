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

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.CommandAvailability;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.command.annotation.OptionValues;
import org.springframework.shell.table.BorderStyle;
import org.springframework.shell.table.CellMatchers;
import org.springframework.shell.table.NoWrapSizeConstraints;
import org.springframework.shell.table.TableBuilder;
import org.springframework.shell.table.TableModel;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.cli.command.CommandConstants;
import com.aestallon.storageexplorer.cli.service.StorageInstanceContext;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryMeta;
import com.aestallon.storageexplorer.core.util.Uris;

@Component
@Command
public class LoadCommand {

  private final StorageInstanceContext storageInstanceContext;

  public LoadCommand(StorageInstanceContext storageInstanceContext) {
    this.storageInstanceContext = storageInstanceContext;
  }

  @Command(command = "load", description = "Load a Storage Entry.",
      group = CommandConstants.COMMAND_GROUP_STORAGE)
  @CommandAvailability(provider = CommandConstants.REQUIRES_STORAGE)
  public void load(CommandContext ctx,
                   @Option(
                       longNames = "uri",
                       shortNames = 'u',
                       required = true,
                       arity = CommandRegistration.OptionArity.EXACTLY_ONE,
                       arityMin = 1,
                       arityMax = 1)
                   @OptionValues(provider = CommandConstants.COMPLETION_PROPOSAL_URI) String uriStr,
                   @Option(
                       longNames = "version",
                       shortNames = 'v',
                       required = false,
                       arity = CommandRegistration.OptionArity.ZERO_OR_ONE,
                       defaultValue = "LATEST",
                       arityMin = 0,
                       arityMax = 1)
                   @OptionValues(
                       provider = CommandConstants.COMPLETION_PROPOSAL_VERSION) String version) {
    final var storageInstance = storageInstanceContext
        .current()
        .orElseThrow(() -> new IllegalStateException(
            "No storage instance selected! Use the 'use' command to select one!"));

    final URI uri = Uris
        .parse(uriStr)
        .orElseThrow(() -> new IllegalArgumentException("Invalid URI: [%s]!".formatted(uriStr)));
    final var targetVersion = TargetVersion
        .parse(version)
        .orElseThrow(() -> new IllegalArgumentException(
            "Invalid version: [%s]! Accepted values: non-negative integers or the LATEST literal!".formatted(
                version)));

    final var storageEntry = storageInstance
        .acquire(uri)
        .orElseThrow(() -> new IllegalArgumentException(
            "Could not acquire entry corresponding to URI: [%s]! Check if your URI is correct!".formatted(
                uriStr)));
    StorageEntryInspectorWriter.of(storageEntry, targetVersion).write(ctx);
    storageInstanceContext.offerCompletion(storageEntry
        .uriProperties().stream()
        .map(UriProperty::uri)
        .toList());
  }

  private sealed interface TargetVersion {

    record Latest() implements TargetVersion {

      @Override
      public String toString() {
        return "LATEST";
      }

    }


    record Exact(long version) implements TargetVersion {

      @Override
      public String toString() {
        return String.valueOf(version);
      }

    }

    static Optional<TargetVersion> parse(String version) {
      if (version.equalsIgnoreCase("latest")) {
        return Optional.of(new Latest());
      } else {
        try {
          final var v = Long.parseLong(version);
          return v < 0 ? Optional.empty() : Optional.of(new Exact(v));
        } catch (NumberFormatException e) {
          return Optional.empty();
        }
      }
    }

  }


  private abstract static sealed class StorageEntryInspectorWriter<T extends StorageEntry> {

    static StorageEntryInspectorWriter<? extends StorageEntry> of(StorageEntry storageEntry,
                                                                  TargetVersion targetVersion) {
      return switch (storageEntry) {
        case ObjectEntry oe -> new ObjectEntryInspectorWriter(oe, targetVersion);
        case SequenceEntry se -> new SequenceEntryInspectorWriter(se);
        case ListEntry le -> new ListEntryInspectorWriter(le);
        case MapEntry me -> new MapEntryInspectorWriter(me);
      };
    }

    protected final T storageEntry;

    protected StorageEntryInspectorWriter(T storageEntry) {
      this.storageEntry = storageEntry;
    }

    abstract void write(CommandContext ctx);

    void writeErr(CommandContext ctx, String message) {
      throw new IllegalStateException("Could not load %s [ uri: %s ]: %s".formatted(
          storageEntry.getClass().getSimpleName(),
          storageEntry.uri(),
          message));
    }

    void writeTable(CommandContext ctx, final TableModel tableModel) {
      final var table = new TableBuilder(tableModel)
          .addHeaderAndVerticalsBorders(BorderStyle.oldschool)
          .on(CellMatchers.table()).addSizer(new NoWrapSizeConstraints())
          .build();
      final var terminal = ctx.getTerminal();
      final String res = table.render(terminal.getWidth());

      final var writer = terminal.writer();
      writer.println(res);
    }

    void flush(CommandContext ctx) {
      ctx.getTerminal().writer().flush();
    }

    private static final class ObjectEntryInspectorWriter
        extends StorageEntryInspectorWriter<ObjectEntry> {

      private final TargetVersion targetVersion;

      public ObjectEntryInspectorWriter(ObjectEntry storageEntry, TargetVersion targetVersion) {
        super(storageEntry);
        this.targetVersion = targetVersion;
      }

      @Override
      void write(CommandContext ctx) {
        final ObjectEntryLoadResult objectEntryLoadResult = storageEntry.tryLoad().get();
        switch (objectEntryLoadResult) {
          case ObjectEntryLoadResult.Err(var msg) -> writeErr(ctx, msg);
          case ObjectEntryLoadResult.SingleVersion sv -> writeSingleVersion(ctx, sv);
          case ObjectEntryLoadResult.MultiVersion mv -> writeMultiVersion(ctx, mv);
        }
      }

      private void writeSingleVersion(CommandContext ctx, ObjectEntryLoadResult.SingleVersion sv) {
        final var tableModel = new EntryMetaTableModel(sv.meta(), storageEntry.getDisplayName(sv));
        writeObjectTableInternal(ctx, sv, tableModel);
      }

      private void writeObjectTableInternal(CommandContext ctx,
                                            ObjectEntryLoadResult.SingleVersion sv,
                                            EntryMetaTableModel tableModel) {
        writeTable(ctx, tableModel);
        writeOamStr(ctx, sv);
        writeTable(ctx, tableModel);
        flush(ctx);
      }

      private void writeMultiVersion(CommandContext ctx, ObjectEntryLoadResult.MultiVersion mv) {
        final ObjectEntryLoadResult.SingleVersion sv;
        final long versionLoaded;
        switch (targetVersion) {

          case TargetVersion.Latest latest -> {
            sv = mv.head();
            versionLoaded = mv.versions().size() - 1;
          }

          case TargetVersion.Exact(long v) -> {
            final var versions = mv.versions();
            if (v >= versions.size()) {
              sv = null;
              versionLoaded = -1L;
            } else {
              sv = versions.get((int) v);
              versionLoaded = v;
            }
          }

        }
        if (sv == null) {
          writeErr(ctx, "Could not find version %s (Highest available version is %d)!".formatted(
              targetVersion.toString(),
              mv.versions().size() - 1));
          return;
        }

        final var tableModel = new EntryMetaTableModel(
            sv.meta(),
            storageEntry.getDisplayName(sv),
            versionLoaded);
        writeObjectTableInternal(ctx, sv, tableModel);
      }

      private void writeOamStr(CommandContext ctx, ObjectEntryLoadResult.SingleVersion sv) {
        ctx.getTerminal().writer().println(sv.oamStr());
      }

      // +------------------------------------+
      // | SCHEMA | NAME | VERSION | CREATION |
      // +--------+------+---------+----------+
      // |  foo   | bar  |    5    |    ??    |
      // +------------------------------------+
      private static final class EntryMetaTableModel extends TableModel {

        private final ObjectEntryMeta meta;
        private final boolean svPolicy;
        private final long version;
        private final String displayName;

        private EntryMetaTableModel(ObjectEntryMeta meta,
                                    String displayName) {
          this.meta = meta;
          this.svPolicy = true;
          this.version = -1L;
          this.displayName = displayName;
        }

        private EntryMetaTableModel(ObjectEntryMeta meta,
                                    String displayName,
                                    long version) {
          this.meta = meta;
          this.svPolicy = false;
          this.version = version;
          this.displayName = displayName;
        }

        @Override
        public int getRowCount() {
          return 2;
        }

        @Override
        public int getColumnCount() {
          return 4;
        }

        @Override
        public Object getValue(int row, int column) {
          return switch (row) {

            case 0 -> switch (column) {
              case 0 -> "SCHEMA";
              case 1 -> "NAME";
              case 2 -> "VERSION";
              case 3 -> "CREATION";
              default -> "UNKNOWN";
            };

            case 1 -> switch (column) {
              case 0 -> meta.storageSchema();
              case 1 -> displayName;
              case 2 -> svPolicy ? "SINGLE" : String.valueOf(version);
              case 3 -> String.valueOf(meta.createdAt());
              default -> "UNKNOWN";
            };

            default -> "UNKNOWN";

          };
        }

      }

    }


    private static final class SequenceEntryInspectorWriter
        extends StorageEntryInspectorWriter<SequenceEntry> {

      public SequenceEntryInspectorWriter(SequenceEntry storageEntry) {
        super(storageEntry);
      }

      @Override
      void write(CommandContext ctx) {
        ctx.getTerminal().writer().println(storageEntry.current());
        flush(ctx);
      }

    }


    private static final class ListEntryInspectorWriter
        extends StorageEntryInspectorWriter<ListEntry> {

      public ListEntryInspectorWriter(ListEntry storageEntry) {
        super(storageEntry);
      }

      @Override
      void write(CommandContext ctx) {
        final var model = new CollectionEntryTableModel(storageEntry);
        writeTable(ctx, model);
        flush(ctx);
      }

    }


    private static final class MapEntryInspectorWriter
        extends StorageEntryInspectorWriter<MapEntry> {

      MapEntryInspectorWriter(MapEntry storageEntry) {
        super(storageEntry);
      }

      @Override
      void write(CommandContext ctx) {
        final var model = new CollectionEntryTableModel(storageEntry);
        writeTable(ctx, model);
        flush(ctx);
      }

    }

  }


  private static final class CollectionEntryTableModel extends TableModel {

    private final List<UriProperty> uris;
    private final String firstColHeader;

    private CollectionEntryTableModel(ListEntry listEntry) {
      uris = listEntry
          .uriProperties().stream()
          .sorted()
          .toList();
      firstColHeader = "INDEX";
    }

    private CollectionEntryTableModel(MapEntry mapEntry) {
      uris = mapEntry
          .uriProperties().stream()
          .sorted()
          .toList();
      firstColHeader = "KEY";
    }

    @Override
    public int getRowCount() {
      return uris.size() + 1;
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValue(int row, int column) {
      if (row == 0) {
        return column == 0 ? firstColHeader : "URI";
      }

      final var uriProperty = uris.get(row - 1);
      return switch (column) {
        case 0 -> UriProperty.Segment.asString(uriProperty.segments());
        case 1 -> uriProperty.uri();
        default -> "UNKNOWN";
      };
    }

  }

}
