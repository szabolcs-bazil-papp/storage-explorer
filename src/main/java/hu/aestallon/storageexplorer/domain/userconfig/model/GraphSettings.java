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

package hu.aestallon.storageexplorer.domain.userconfig.model;

import java.util.Objects;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonPropertyOrder({
    "graphTraversalInboundLimit",
    "graphTraversalOutboundLimit",
})
@JsonTypeName("GraphSettings")
public final class GraphSettings {

  @JsonProperty("graphTraversalInboundLimit")
  private Integer graphTraversalInboundLimit = 0;

  @JsonProperty("graphTraversalOutboundLimit")
  private Integer graphTraversalOutboundLimit = 2;

  public GraphSettings() {}

  public GraphSettings graphTraversalInboundLimit(Integer graphTraversalInboundLimit) {
    this.graphTraversalInboundLimit = graphTraversalInboundLimit;
    return this;
  }

  @NotNull
  @JsonProperty("graphTraversalInboundLimit")
  public Integer getGraphTraversalInboundLimit() {
    return graphTraversalInboundLimit;
  }

  public void setGraphTraversalInboundLimit(Integer graphTraversalInboundLimit) {
    this.graphTraversalInboundLimit = graphTraversalInboundLimit;
  }

  public GraphSettings graphTraversalOutboundLimit(Integer graphTraversalOutboundLimit) {
    this.graphTraversalOutboundLimit = graphTraversalOutboundLimit;
    return this;
  }

  @JsonProperty("graphTraversalOutboundLimit")
  public Integer getGraphTraversalOutboundLimit() {
    return graphTraversalOutboundLimit;
  }

  @JsonProperty("graphTraversalOutboundLimit")
  public void setGraphTraversalOutboundLimit(Integer graphTraversalOutboundLimit) {
    this.graphTraversalOutboundLimit = graphTraversalOutboundLimit;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GraphSettings graphSettings = (GraphSettings) o;
    return Objects.equals(this.graphTraversalInboundLimit, graphSettings.graphTraversalInboundLimit)
        &&
        Objects.equals(this.graphTraversalOutboundLimit, graphSettings.graphTraversalOutboundLimit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        graphTraversalInboundLimit,
        graphTraversalOutboundLimit);
  }

  @Override
  public String toString() {
    return "GraphSettings {\n"
        + "    graphTraversalInboundLimit: "
        + toIndentedString(graphTraversalInboundLimit) + "\n"
        + "    graphTraversalOutboundLimit: "
        + toIndentedString(graphTraversalOutboundLimit) + "\n"
        + "}";
  }

  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}
