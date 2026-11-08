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
 * The domain-shaped input to {@link StorageInstance#newVersion}: add a new version with this
 * payload, redirecting the head to it. Deliberately minimal for now — {@code createdBy}/
 * {@code force}/lock-policy knobs the implementation plan's T4.3 note mentions are
 * engine-construction-time settings today ({@code FsStorageEngine.withWriteOptions}), not
 * per-call ones, so there is nothing yet for this command to carry beyond the payload itself;
 * grow it if/when a per-call override is actually needed.
 */
public record StorageEntryPutCommand(byte[] payload) {
}
