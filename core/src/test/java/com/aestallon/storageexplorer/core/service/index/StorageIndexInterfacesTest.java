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

package com.aestallon.storageexplorer.core.service.index;

import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.index.result.AdjacencyResult;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;
import com.aestallon.storageexplorer.core.service.index.result.MetadataResult;

/**
 * T2.2's interfaces are additive and have no implementation yet — this just confirms the
 * hierarchy actually composes: a single implementation can satisfy all three capability
 * interfaces at once (the realistic case — a concrete {@code StorageIndex} typically implements
 * more than one), {@link StorageIndex#close()} needs no checked-exception handling, and it works
 * in a try-with-resources exactly like any other {@link AutoCloseable}.
 */
class StorageIndexInterfacesTest {

  private static final class Fake implements ContentLoadable, AdjacencyAware, MetadataAware {
    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public StorageId storageId() {
      return new StorageId(null);
    }

    @Override
    public EntryResolution resolve(final EntryUri uri) {
      return new EntryResolution.Unknown(uri, "fake, nothing is known");
    }

    @Override
    public ContentLoadResult load(final com.aestallon.storageexplorer.core.model.entry.StorageEntry entry) {
      return new ContentLoadResult.Err(entry, "fake");
    }

    @Override
    public AdjacencyResult outgoingReferences(
        final com.aestallon.storageexplorer.core.model.entry.StorageEntry entry) {
      return new AdjacencyResult.Err(entry, "fake");
    }

    @Override
    public MetadataResult metadataOf(final com.aestallon.storageexplorer.core.model.entry.StorageEntry entry) {
      return new MetadataResult.Err(entry, "fake");
    }

    @Override
    public void close() {
      closed.set(true);
    }
  }

  @Test
  void oneImplementationSatisfiesEveryCapabilityAtOnce() {
    final Fake fake = new Fake();
    final StorageIndex asBase = fake;
    final ContentLoadable asLoadable = fake;
    final AdjacencyAware asAdjacency = fake;
    final MetadataAware asMetadata = fake;

    final EntryUri uri = EntryUri.parse("s:/a/b").orElseThrow();
    assertThat(asBase.resolve(uri)).isInstanceOf(EntryResolution.Unknown.class);
    assertThat(asLoadable.load(null)).isInstanceOf(ContentLoadResult.Err.class);
    assertThat(asAdjacency.outgoingReferences(null)).isInstanceOf(AdjacencyResult.Err.class);
    assertThat(asMetadata.metadataOf(null)).isInstanceOf(MetadataResult.Err.class);
  }

  @Test
  void closeNeedsNoCheckedExceptionHandling_andWorksInTryWithResources() {
    final Fake fake = new Fake();
    try (StorageIndex index = fake) {
      assertThat(fake.closed.get()).isFalse();
    }
    assertThat(fake.closed.get()).isTrue();
  }
}
