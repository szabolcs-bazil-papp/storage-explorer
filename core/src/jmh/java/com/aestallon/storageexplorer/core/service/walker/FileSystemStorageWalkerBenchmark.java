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

package com.aestallon.storageexplorer.core.service.walker;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@BenchmarkMode(Mode.AverageTime)
@Fork(1)
public class FileSystemStorageWalkerBenchmark {

  private static final Path FS_BASE_DIRECTORY = Path.of("C:\\Users\\papps\\kivipo-fs");


  @Benchmark
  public void legacy0(Blackhole blackhole) {
    blackhole.consume(FileSystemStorageWalkerFactory
        .create(
            FS_BASE_DIRECTORY,
            FileSystemStorageWalkerFactory.Type.LEGACY_0)
        .walk(IndexingTarget.any())
        .toList());
  }

  @Benchmark
  public void legacy1(Blackhole blackhole) {
    blackhole.consume(FileSystemStorageWalkerFactory
        .create(
            FS_BASE_DIRECTORY,
            FileSystemStorageWalkerFactory.Type.LEGACY_1)
        .walk(IndexingTarget.any())
        .toList());
  }

  @Benchmark
  public void current(Blackhole blackhole) {
    blackhole.consume(FileSystemStorageWalkerFactory
        .create(
            FS_BASE_DIRECTORY,
            FileSystemStorageWalkerFactory.Type.DEFAULT)
        .walk(IndexingTarget.any())
        .toList());
  }
}
