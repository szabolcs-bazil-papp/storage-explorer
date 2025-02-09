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

package com.aestallon.storageexplorer.core.model.loading;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public sealed interface ObjectEntryLoadRequest {

  ObjectEntryLoadResult get();


  final class FileSystemObjectEntryLoadRequest implements ObjectEntryLoadRequest {

    private final ObjectEntryLoadResult result;

    public FileSystemObjectEntryLoadRequest(final ObjectEntryLoadResult result) {
      this.result = result;
    }

    @Override
    public ObjectEntryLoadResult get() {
      return result;
    }

  }


  final class RelationalDatabaseObjectEntryLoadRequest implements ObjectEntryLoadRequest {

    private final Future<ObjectEntryLoadResult> result;

    public RelationalDatabaseObjectEntryLoadRequest(final Future<ObjectEntryLoadResult> result) {
      this.result = result;
    }

    @Override
    public ObjectEntryLoadResult get() {
      try {
        return result.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return ObjectEntryLoadResults.err("ObjectEntry loading request interrupted");
      } catch (ExecutionException e) {
        return ObjectEntryLoadResults.err(e.getMessage());
      }
    }

  }

}
