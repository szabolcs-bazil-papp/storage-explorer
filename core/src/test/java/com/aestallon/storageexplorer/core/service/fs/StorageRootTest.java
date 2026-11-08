/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.core.service.fs;

import java.nio.file.Path;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.EntryUri;

/**
 * Mirrors {@code sb4a-fs}'s own {@code layout} module test suite
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/layout.rs}).
 */
class StorageRootTest {

  private static EntryUri uri(final String text) {
    return EntryUri.parse(text).orElseThrow();
  }

  @Test
  @DisplayName("hexPath table: the documented examples")
  void hexPathTable() {
    assertThat(StorageRoot.hexPath(0)).isEqualTo("/00");
    assertThat(StorageRoot.hexPath(5)).isEqualTo("/05");
    assertThat(StorageRoot.hexPath(15)).isEqualTo("/0F");
    assertThat(StorageRoot.hexPath(16)).isEqualTo("/10");
    assertThat(StorageRoot.hexPath(255)).isEqualTo("/FF");
    assertThat(StorageRoot.hexPath(256)).isEqualTo("/1/00");
    assertThat(StorageRoot.hexPath(511)).isEqualTo("/1/FF");
    assertThat(StorageRoot.hexPath(4095)).isEqualTo("/F/FF");
    assertThat(StorageRoot.hexPath(4096)).isEqualTo("/1/0/00");
    assertThat(StorageRoot.hexPath(0x1A305)).isEqualTo("/1/A/3/05");
    // u64::MAX's bit pattern, as a Java long
    assertThat(StorageRoot.hexPath(-1L)).isEqualTo("/F/F/F/F/F/F/F/F/F/F/F/F/F/F/FF");
  }

  /** The inverse of {@code hexPath}, used to check it is a bijection. */
  private static long parseHexPath(final String path) {
    final String[] parts = path.substring(1).split("/");
    final String name = parts[parts.length - 1];
    long bucket = 0L;
    for (int i = 0; i < parts.length - 1; i++) {
      bucket = (bucket << 4) | Long.parseLong(parts[i], 16);
    }
    return (bucket << 8) | Long.parseLong(name, 16);
  }

  @Test
  @DisplayName("hexPath round-trips through its own inverse, across random long values")
  void hexPathRoundTrips() {
    final Random random = new Random(42);
    for (int i = 0; i < 10_000; i++) {
      final long index = random.nextLong();
      assertThat(parseHexPath(StorageRoot.hexPath(index))).isEqualTo(index);
    }
  }

  @Test
  @DisplayName("hexPath is strictly ordered within one bucket folder")
  void hexPathIsStrictlyOrderedWithinABucket() {
    final Random random = new Random(7);
    for (int i = 0; i < 1_000; i++) {
      final long bucket = Math.floorMod(random.nextLong(), 1_000_000L);
      final long a = Math.floorMod(random.nextLong(), 256L);
      final long b = Math.floorMod(random.nextLong(), 256L);
      final String pa = StorageRoot.hexPath((bucket << 8) | a);
      final String pb = StorageRoot.hexPath((bucket << 8) | b);
      assertThat(Long.signum(Long.compare(a, b))).isEqualTo(Long.signum(pa.compareTo(pb)));
    }
  }

  @Test
  @DisplayName("Object-level file paths")
  void objectLevelFiles() {
    final StorageRoot root = new StorageRoot(Path.of("C:/data/dev-fs"));
    final EntryUri u = uri("org:/org_x_StorageSettings/settings");
    final Path base = Path.of("C:/data/dev-fs").resolve("org").resolve("org_x_StorageSettings")
        .resolve("settings");

    assertThat(root.versionDir(u)).isEqualTo(base);
    assertThat(root.objectFile(u)).isEqualTo(Path.of(base + ".o"));
    assertThat(root.transactionFile(u)).isEqualTo(Path.of(base + ".t"));
    assertThat(root.lockFile(u)).isEqualTo(Path.of(base + ".l"));
    assertThat(root.relationFile(u, 4)).isEqualTo(Path.of(base + "/04.r"));
  }

  @Test
  @DisplayName("objectFile and uriOfObjectFile are inverses")
  void objectFileAndUriAreInverse() {
    final StorageRoot root = new StorageRoot(Path.of("/r"));
    final EntryUri u = uri("org:/org_x_StorageSettings/settings");

    assertThat(root.uriOfObjectFile(root.objectFile(u))).contains(u);
    assertThat(root.uriOfObjectFile(root.transactionFile(u))).isEmpty();
    assertThat(root.uriOfObjectFile(Path.of("/elsewhere/org/a.o"))).isEmpty();
    assertThat(root.uriOfObjectFile(Path.of("/r/a.o"))).isEmpty();
  }

  @Test
  @DisplayName("Classic version locations")
  void classicLocations() {
    final StorageRoot root = new StorageRoot(Path.of("/r"));
    final EntryUri u = uri("s:/a/b");

    VersionLocation loc = root.locateVersion(u, 2, Strategy.CLASSIC, null);
    assertThat(loc).isInstanceOf(VersionLocation.Classic.class);
    assertThat(((VersionLocation.Classic) loc).file()).isEqualTo(Path.of("/r/s/a/b/02"));

    loc = root.locateVersion(u, 256, Strategy.CLASSIC, null);
    assertThat(((VersionLocation.Classic) loc).file()).isEqualTo(Path.of("/r/s/a/b/1/00"));
  }

  @Test
  @DisplayName("Indexed version locations")
  void indexedLocations() {
    final StorageRoot root = new StorageRoot(Path.of("/r"));
    final EntryUri u = uri("s:/a/b");

    assertThat(root.locateVersion(u, 0, Strategy.INDEXED, 256))
        .isEqualTo(new VersionLocation.Indexed(Path.of("/r/s/a/b/00.iv"), 0, 256));
    assertThat(root.locateVersion(u, 255, Strategy.INDEXED, 256))
        .isEqualTo(new VersionLocation.Indexed(Path.of("/r/s/a/b/00.iv"), 255, 256));
    assertThat(root.locateVersion(u, 256, Strategy.INDEXED, 256))
        .isEqualTo(new VersionLocation.Indexed(Path.of("/r/s/a/b/01.iv"), 0, 256));
    // file number 256 is the first one that needs a bucket folder
    assertThat(root.locateVersion(u, 256L * 256 + 7, Strategy.INDEXED, 256))
        .isEqualTo(new VersionLocation.Indexed(Path.of("/r/s/a/b/1/00.iv"), 7, 256));
  }

  @Test
  @DisplayName("Indexed layout needs both the strategy and an index size; either alone is classic")
  void indexedNeedsStrategyAndSize() {
    final StorageRoot root = new StorageRoot(Path.of("/r"));
    final EntryUri u = uri("s:/a/b");

    assertThat(root.locateVersion(u, 3, Strategy.INDEXED, null))
        .isInstanceOf(VersionLocation.Classic.class);
    assertThat(root.locateVersion(u, 3, Strategy.CLASSIC, 256))
        .isInstanceOf(VersionLocation.Classic.class);
    assertThat(root.locateVersion(u, 3, Strategy.INDEXED, 0))
        .isInstanceOf(VersionLocation.Classic.class);
  }

  @Test
  @DisplayName("A non-default index size is honored")
  void nonDefaultIndexSize() {
    final StorageRoot root = new StorageRoot(Path.of("/r"));
    final EntryUri u = uri("s:/a/b");

    assertThat(root.locateVersion(u, 1000, Strategy.INDEXED, 100))
        .isEqualTo(new VersionLocation.Indexed(Path.of("/r/s/a/b/0A.iv"), 0, 100));
  }
}
