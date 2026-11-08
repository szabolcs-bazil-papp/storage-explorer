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

package com.aestallon.storageexplorer.core.service.engine;

import java.io.InputStream;

/**
 * A handle to an entry's content — not pre-materialized data. One type serves every entry kind,
 * including {@code ObjectStreamEntry}/{@code TableDataEntry}: this is what resolves the question
 * of whether {@code .os}/{@code .td} content needs its own capability interface (it doesn't).
 *
 * <p>{@link #openStream()} gives the raw bytes, for callers that never need a parsed tree at all
 * ({@code UriLeafScanner}, T2.4; ArcScript projection evaluation that only needs a few fields).
 * {@link #materialize()} does the full load, kind-aware, for inspector views and anything else
 * that wants the whole thing. A handle may be used for either, independently, more than once —
 * implementations decide for themselves whether repeated calls re-read the underlying storage or
 * reuse a cached read; callers must not assume either way.
 */
public interface ContentHandle {

  /** The entry's raw bytes, unparsed. Callers are responsible for closing the stream. */
  InputStream openStream();

  /** The entry's content, fully loaded and kind-aware. */
  LoadedContent materialize();
}
