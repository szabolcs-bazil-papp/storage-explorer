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

package com.aestallon.storageexplorer.core.userconfig.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import static java.util.stream.Collectors.toCollection;

/*
 * [...]/storage-explorer/
 *              |
 *              +----------- [storage-id1]/
 *              |                 |
 *              |                 +------------ My Script.se
 *              |                 |
 *              |                 +------------ My Script (1).se
 *              |
 *              +----------- [storage-id2]/
 *                                |
 *                                +------------ My Script.se
 */
public final class ArcScriptFileService {

  private static final String EXTENSION = ".as";
  public static final String DIR_ARC_SCRIPT = "arc-script";

  private static void write(String text, FileAndTitle fileAndTitle) throws IOException {
    Files.writeString(
        fileAndTitle.file(),
        text,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private final Path settingsFolder;

  ArcScriptFileService(Path settingsFolder) {
    this.settingsFolder = settingsFolder;
  }

  /**
   * Persists a script in a new file.
   *
   * @param storageId the {@link StorageId} identifying the storage this script belongs to, not
   *     null
   * @param title the intended {@link String} title for the script, not null
   * @param text the {@link String} contents of the script, not null
   *
   * @return the result of an operation, represented as an {@link ArcScriptIoResult}
   */
  public ArcScriptIoResult saveAsNew(final StorageId storageId,
                                     final String title,
                                     final String text) {
    try {
      final var storageDir = storageDir(storageId);
      final var fileAndTitle = scriptFile(storageDir, title);
      write(text, fileAndTitle);
      return ArcScriptIoResult.ok(StoredArcScript.of(storageId, fileAndTitle.title(), text));

    } catch (final IOException e) {
      return ArcScriptIoResult.err(e);
    }
  }

  private Path storageDir(final StorageId storageId) throws IOException {
    final Path storagePath = settingsFolder.resolve(DIR_ARC_SCRIPT).resolve(storageId.toString());
    return Files.createDirectories(storagePath);
  }

  private FileAndTitle scriptFile(final Path storageDir, final String title)
      throws FilenameNotAvailableException {
    String filename = title + EXTENSION;
    if (!Files.exists(storageDir.resolve(filename))) {
      return new FileAndTitle(storageDir.resolve(filename), title);
    }

    int cnt = 0;
    while (++cnt < 100) {
      final String titleToUse = "%s (%02d)".formatted(title, cnt);
      filename = titleToUse + EXTENSION;
      if (!Files.exists(storageDir.resolve(filename))) {
        return new FileAndTitle(storageDir.resolve(filename), titleToUse);
      }
    }

    throw new FilenameNotAvailableException(title, cnt);
  }

  /**
   * @param storedArcScript
   * @param newTitle
   *
   * @return
   */
  public ArcScriptIoResult rename(final StoredArcScript storedArcScript, final String newTitle) {
    try {
      final var storageDir = storageDir(storedArcScript.storageId());
      final var file = storageDir.resolve(storedArcScript.title() + EXTENSION);
      if (!Files.exists(file)) {
        return saveAsNew(storedArcScript.storageId(), newTitle, storedArcScript.script());
      }

      final var fileAndTitle = scriptFile(storageDir, newTitle);
      Files.move(file, fileAndTitle.file());

      return ArcScriptIoResult.ok(StoredArcScript.of(
          storedArcScript.storageId(),
          fileAndTitle.title(),
          storedArcScript.script()));
    } catch (final IOException e) {
      return ArcScriptIoResult.err(e);
    }
  }

  public ArcScriptIoResult save(final StoredArcScript storedArcScript, final String newText) {
    try {
      final var storageDir = storageDir(storedArcScript.storageId());
      final var file = storageDir.resolve(storedArcScript.title() + EXTENSION);
      if (!Files.exists(file)) {
        return saveAsNew(storedArcScript.storageId(), storedArcScript.title(), newText);
      }

      final var fileAndTitle = new FileAndTitle(file, storedArcScript.title());
      write(newText, fileAndTitle);

      return ArcScriptIoResult.ok(StoredArcScript.of(
          storedArcScript.storageId(),
          storedArcScript.title(),
          newText));
    } catch (final IOException e) {
      return ArcScriptIoResult.err(e);
    }
  }

  public ArcScriptIoResult load(final StorageId storageId, final String title) {
    try {
      final var storageDir = storageDir(storageId);
      final var file = storageDir.resolve(title + EXTENSION);
      final var content = Files.readString(file, StandardCharsets.UTF_8);
      return ArcScriptIoResult.ok(StoredArcScript.of(storageId, title, content));
    } catch (final IOException e) {
      return ArcScriptIoResult.err(e);
    }
  }

  public List<StoredArcScript> loadAll(final StorageId storageId) {
    try {
      final var storageDir = storageDir(storageId);
      try (final var files = Files.list(storageDir)) {
        return files.filter(Files::isRegularFile)
            .filter(it -> it.getFileName().toString().endsWith(EXTENSION))
            .flatMap(it -> {
              try {
                final String filename = it.getFileName().toString();
                return Stream.of(Pair.of(
                    filename.substring(0, filename.lastIndexOf('.')),
                    Files.readString(it, StandardCharsets.UTF_8)));
              } catch (final IOException e) {
                return Stream.empty();
              }
            })
            .map(p -> StoredArcScript.of(storageId, p.a(), p.b()))
            .toList();
      }

    } catch (final IOException e) {
      return Collections.emptyList();
    }
  }

  public void deleteAll(final StorageId storageId) {
    try {
      final var storageDir = storageDir(storageId);
      Files.walkFileTree(storageDir, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult postVisitDirectory(
            Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(
            Path file, BasicFileAttributes attrs)
            throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (final IOException ignored) {
      // :)
    }
  }
  
  public void delete(final StorageId storageId, final String title) {
    try {
      final var storageDir = storageDir(storageId);
      final var file = storageDir.resolve(title + EXTENSION);
      Files.deleteIfExists(file);
    } catch (final IOException ignored) {
      // :)
    }
  }

  public Map<StorageId, List<String>> checkAllAvailable() {
    final Path root = settingsFolder.resolve(DIR_ARC_SCRIPT);
    final List<StorageId> storageIds = new ArrayList<>();
    try (final var rootChildren = Files.list(root)) {
      rootChildren.filter(Files::isDirectory)
          .map(it -> it.getFileName().toString())
          .flatMap(it -> {
            try {
              return Stream.of(UUID.fromString(it));
            } catch (final IllegalArgumentException e) {
              return Stream.empty();
            }
          })
          .map(StorageId::new)
          .forEach(storageIds::add);
    } catch (IOException e) {
      return Collections.emptyMap();
    }

    final Map<StorageId, List<String>> ret = new HashMap<>();
    for (final var storageId : storageIds) {
      try {
        final var storageDir = storageDir(storageId);
        try (final var files = Files.list(storageDir)) {
          final List<String> titles = files.filter(Files::isRegularFile)
              .filter(it -> it.getFileName().toString().endsWith(EXTENSION))
              .map(it -> {
                final String filename = it.getFileName().toString();
                return filename.substring(0, filename.lastIndexOf('.'));
              })
              .collect(toCollection(ArrayList::new));
          ret.put(storageId, titles);
        }
      } catch (final IOException e) {
        ret.put(storageId, new ArrayList<>());
      }
    }
    return ret;
  }


  private record FileAndTitle(Path file, String title) {}


  public sealed interface ArcScriptIoResult {

    static ArcScriptIoResult ok(final StoredArcScript storedArcScript) {
      return new Ok(storedArcScript);
    }

    static ArcScriptIoResult err(final Exception e) {
      return new Err(e.getMessage());
    }

    record Err(String msg) implements ArcScriptIoResult {}


    record Ok(StoredArcScript storedArcScript) implements ArcScriptIoResult {}

  }


  private static final class FilenameNotAvailableException extends IOException {

    public FilenameNotAvailableException(final String title, final int fileCount) {
      super(
          "There are already %d script files named %s for this Storage! Pick another title!".formatted(
              fileCount, title));
    }

  }

}
