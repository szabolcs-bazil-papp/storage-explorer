/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package hu.aestallon.storageexplorer.domain.userconfig.event;

import java.util.Objects;

public class GraphConfigChanged {

  private final int newInboundLimit;
  private final int newOutboundLimit;

  public GraphConfigChanged(int newInboundLimit, int newOutboundLimit) {
    this.newInboundLimit = newInboundLimit;
    this.newOutboundLimit = newOutboundLimit;
  }

  public int newInboundLimit() {
    return newInboundLimit;
  }

  public int newOutboundLimit() {
    return newOutboundLimit;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    GraphConfigChanged that = (GraphConfigChanged) o;
    return newInboundLimit == that.newInboundLimit && newOutboundLimit == that.newOutboundLimit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(newInboundLimit, newOutboundLimit);
  }

  @Override
  public String toString() {
    return "GraphConfigChanged{" +
        "newInboundLimit=" + newInboundLimit +
        ", newOutboundLimit=" + newOutboundLimit +
        '}';
  }

}
