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

package com.aestallon.storageexplorer.arcscript.internal.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.aestallon.storageexplorer.arcscript.api.QueryInstruction;
import com.aestallon.storageexplorer.arcscript.api.YieldInstruction;
import com.aestallon.storageexplorer.arcscript.internal.ArcLiteral;

public class YieldInstructionImpl implements YieldInstruction {

  public final List<QueryInstructionImpl.ShowColumn> _columns = new ArrayList<>();

  public ArcLiteral propertyMissing(String name) {
    return new ArcLiteral(name);
  }

  @Override
  public QueryInstruction.Column col(String prop) {
    final var col = new QueryInstructionImpl.ShowColumn(prop);
    this._columns.add(col);
    return col;
  }

  @Override
  public void col(String prop, String... props) {
    if (props == null) {
      throw new IllegalArgumentException("property cannot be null or empty");
    }

    this._columns.add(new QueryInstructionImpl.ShowColumn(prop));
    for (final String p : props) {
      this._columns.add(new QueryInstructionImpl.ShowColumn(p));
    }
  }

  @Override
  public QueryInstruction.Column col(ArcLiteral al) {
    return col(al.toString());
  }

  @Override
  public void col(ArcLiteral al, ArcLiteral... als) {
    col(al.toString(), Arrays.stream(als).map(ArcLiteral::toString).toArray(String[]::new));
  }

}
