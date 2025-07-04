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

package com.aestallon.storageexplorer.client.userconfig.service;

import java.util.Objects;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;

public final class StoredArcScript {

  static StoredArcScript of(final StorageId storageId,
                            final String title,
                            final String script) {
    final var it = new StoredArcScript(storageId);
    it.title(title);
    it.script(script);
    return it;
  }

  private final StorageId storageId;
  private String title;
  private String script;

  public StoredArcScript(StorageId storageId) {
    this.storageId = storageId;
  }

  public StorageId storageId() {
    return storageId;
  }

  public String title() {
    return title;
  }

  void title(String title) {
    this.title = title;
  }

  public String script() {
    return script;
  }

  void script(String script) {
    this.script = script;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    StoredArcScript that = (StoredArcScript) o;
    return Objects.equals(storageId, that.storageId) && Objects.equals(title,
        that.title) && Objects.equals(script, that.script);
  }

  @Override
  public int hashCode() {
    return Objects.hash(storageId, title, script);
  }

}
