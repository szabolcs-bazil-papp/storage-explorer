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

package com.aestallon.storageexplorer.core.model.instance;

/**
 * The domain-shaped input to {@link StorageInstance#amend}: replace the payload of version
 * {@code serial} in place (the version record itself is untouched, the head is not redirected).
 *
 * @param serial    which version to amend
 * @param payload   the replacement payload
 * @param touchHead bump the entry's own modification marker, so caches keyed on it notice the
 *                  change underneath an unchanged descriptor (default {@code true} — see {@code
 *                  AmendOptions}'s own javadoc for why this matters on FS)
 */
public record StorageEntryAmendCommand(long serial, byte[] payload, boolean touchHead) {

  public static StorageEntryAmendCommand of(final long serial, final byte[] payload) {
    return new StorageEntryAmendCommand(serial, payload, true);
  }
}
