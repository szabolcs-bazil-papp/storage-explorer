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

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * One version of an object as it is stored.
 *
 * @param serial          the data serial ({@code .v<N>}); empty for single-version objects
 * @param version         the version record: part 0 of the version file, or the descriptor's
 *                        current version of a single-version object
 * @param payload         the entity JSON. Opaque to this engine. Still encrypted if
 *                        {@code encryptionRule} is not {@code none}.
 * @param encryptionRule  the rule the payload is encrypted with, {@code none} if it is not
 */
public record Entity(OptionalLong serial, Optional<ObjectVersion> version, byte[] payload,
                      String encryptionRule) {

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Entity other)) {
      return false;
    }
    return serial.equals(other.serial) && version.equals(other.version)
        && Arrays.equals(payload, other.payload) && encryptionRule.equals(other.encryptionRule);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serial, version, Arrays.hashCode(payload), encryptionRule);
  }
}
