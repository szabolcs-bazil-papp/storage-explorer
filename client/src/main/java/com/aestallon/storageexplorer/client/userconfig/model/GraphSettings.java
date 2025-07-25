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

package com.aestallon.storageexplorer.client.userconfig.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder({
    "graphTraversalInboundLimit",
    "graphTraversalOutboundLimit",
    "blacklistedSchemas",
    "blacklistedTypes",
    "whitelistedSchemas",
    "whitelistedTypes",
    "nodeSizing",
    "nodeColouring",
    "aggressiveDiscovery",
    "layoutAlgorithm"
})
@JsonTypeName("GraphSettings")
public final class GraphSettings {

  @JsonProperty("graphTraversalInboundLimit")
  private int graphTraversalInboundLimit = 0;

  @JsonProperty("graphTraversalOutboundLimit")
  private int graphTraversalOutboundLimit = 2;

  @JsonProperty("blacklistedSchemas")
  private List<String> blacklistedSchemas = new ArrayList<>();
  @JsonProperty("blacklistedTypes")
  private List<String> blacklistedTypes = new ArrayList<>();
  @JsonProperty("whitelistedSchemas")
  private List<String> whitelistedSchemas = new ArrayList<>();
  @JsonProperty("whitelistedTypes")
  private List<String> whitelistedTypes = new ArrayList<>();


  public enum NodeSizing {
    UNIFORM("UNIFORM"),
    OUT_DEGREE("OUT DEGREE"),
    IN_DEGREE("IN DEGREE"),
    DEGREE("DEGREE"),
    SIZE("EST. SIZE"),
    VERSION_COUNT("VERSION COUNT");

    private final String value;

    NodeSizing(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static NodeSizing fromValue(String value) {
      for (NodeSizing b : NodeSizing.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }


  @JsonProperty("nodeSizing")
  private NodeSizing nodeSizing = NodeSizing.UNIFORM;


  public enum NodeColouring {
    UNIFORM("UNIFORM"),
    OUT_DEGREE("OUT DEGREE"),
    IN_DEGREE("IN DEGREE"),
    DEGREE("DEGREE"),
    SIZE("EST. SIZE"),
    TYPE("TYPE"),
    SCHEMA("SCHEMA");

    private final String value;

    NodeColouring(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static NodeColouring fromValue(String value) {
      for (NodeColouring b : NodeColouring.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }


  @JsonProperty("nodeColouring")
  private NodeColouring nodeColouring = NodeColouring.UNIFORM;

  @JsonProperty("aggressiveDiscovery")
  private boolean aggressiveDiscovery = true;


  public enum LayoutAlgorithm {
    SPRING_BOX("SPRING BOX"),
    LINLOG("LINLOG"),
    FORCE_ATLAS2("ForceAtlas2"),
    FORCE_ATLAS2_LINLOG("ForceAtlas2 - LINLOG");

    private final String value;

    LayoutAlgorithm(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static LayoutAlgorithm fromValue(String value) {
      for (LayoutAlgorithm b : LayoutAlgorithm.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }


  @JsonProperty("layoutAlgorithm")
  private LayoutAlgorithm layoutAlgorithm = LayoutAlgorithm.SPRING_BOX;

  public GraphSettings() { /* POJO constructor */ }

  public GraphSettings graphTraversalInboundLimit(int graphTraversalInboundLimit) {
    this.graphTraversalInboundLimit = graphTraversalInboundLimit;
    return this;
  }

  @NotNull
  @JsonProperty("graphTraversalInboundLimit")
  public int getGraphTraversalInboundLimit() {
    return graphTraversalInboundLimit;
  }

  public void setGraphTraversalInboundLimit(int graphTraversalInboundLimit) {
    this.graphTraversalInboundLimit = graphTraversalInboundLimit;
  }

  public GraphSettings graphTraversalOutboundLimit(int graphTraversalOutboundLimit) {
    this.graphTraversalOutboundLimit = graphTraversalOutboundLimit;
    return this;
  }

  @JsonProperty("graphTraversalOutboundLimit")
  public int getGraphTraversalOutboundLimit() {
    return graphTraversalOutboundLimit;
  }

  @JsonProperty("graphTraversalOutboundLimit")
  public void setGraphTraversalOutboundLimit(int graphTraversalOutboundLimit) {
    this.graphTraversalOutboundLimit = graphTraversalOutboundLimit;
  }

  public GraphSettings blacklistedSchemas(List<String> blacklistedSchemas) {
    this.blacklistedSchemas = Objects.requireNonNull(blacklistedSchemas);
    return this;
  }

  @JsonProperty("blacklistedSchemas")
  public List<String> getBlacklistedSchemas() {
    return blacklistedSchemas;
  }

  @JsonProperty("blacklistedSchemas")
  public void setBlacklistedSchemas(List<String> blacklistedSchemas) {
    this.blacklistedSchemas = blacklistedSchemas;
  }

  public GraphSettings blacklistedTypes(List<String> blacklistedTypes) {
    this.blacklistedTypes = Objects.requireNonNull(blacklistedTypes);
    return this;
  }

  @JsonProperty("blacklistedTypes")
  public List<String> getBlacklistedTypes() {
    return blacklistedTypes;
  }

  @JsonProperty("blacklistedTypes")
  public void setBlacklistedTypes(List<String> blacklistedTypes) {
    this.blacklistedTypes = blacklistedTypes;
  }

  public GraphSettings whitelistedSchemas(List<String> whitelistedSchemas) {
    this.whitelistedSchemas = Objects.requireNonNull(whitelistedSchemas);
    return this;
  }

  @JsonProperty("whitelistedSchemas")
  public List<String> getWhitelistedSchemas() {
    return whitelistedSchemas;
  }

  @JsonProperty("whitelistedSchemas")
  public void setWhitelistedSchemas(List<String> whitelistedSchemas) {
    this.whitelistedSchemas = whitelistedSchemas;
  }

  public GraphSettings whitelistedTypes(List<String> whitelistedTypes) {
    this.whitelistedTypes = Objects.requireNonNull(whitelistedTypes);
    return this;
  }

  @JsonProperty("whitelistedTypes")
  public List<String> getWhitelistedTypes() {
    return whitelistedTypes;
  }

  @JsonProperty("whitelistedTypes")
  public void setWhitelistedTypes(List<String> whitelistedTypes) {
    this.whitelistedTypes = whitelistedTypes;
  }

  public GraphSettings nodeSizing(NodeSizing nodeSizing) {
    this.nodeSizing = Objects.requireNonNull(nodeSizing);
    return this;
  }

  @JsonProperty("nodeSizing")
  public NodeSizing getNodeSizing() {
    return nodeSizing;
  }

  @JsonProperty("nodeSizing")
  public void setNodeSizing(NodeSizing nodeSizing) {
    this.nodeSizing = nodeSizing;
  }

  public GraphSettings nodeColouring(NodeColouring nodeColouring) {
    this.nodeColouring = Objects.requireNonNull(nodeColouring);
    return this;
  }

  @JsonProperty("nodeColouring")
  public NodeColouring getNodeColouring() {
    return nodeColouring;
  }

  @JsonProperty("nodeColouring")
  public void setNodeColouring(NodeColouring nodeColouring) {
    this.nodeColouring = nodeColouring;
  }

  public GraphSettings aggressiveDiscovery(boolean aggressiveDiscovery) {
    this.aggressiveDiscovery = aggressiveDiscovery;
    return this;
  }

  @JsonProperty("aggressiveDiscovery")
  public boolean getAggressiveDiscovery() {
    return aggressiveDiscovery;
  }

  @JsonProperty("aggressiveDiscovery")
  public void setAggressiveDiscovery(boolean aggressiveDiscovery) {
    this.aggressiveDiscovery = aggressiveDiscovery;
  }
  
  public GraphSettings layoutAlgorithm(final LayoutAlgorithm layoutAlgorithm) {
    this.layoutAlgorithm = Objects.requireNonNull(layoutAlgorithm);
    return this;
  }

  @JsonProperty("layoutAlgorithm")
  public LayoutAlgorithm getLayoutAlgorithm() {
    return layoutAlgorithm;
  }

  @JsonProperty("layoutAlgorithm")
  public void setLayoutAlgorithm(LayoutAlgorithm layoutAlgorithm) {
    this.layoutAlgorithm = layoutAlgorithm;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) { return false; }
    GraphSettings that = (GraphSettings) o;
    return graphTraversalInboundLimit == that.graphTraversalInboundLimit
           && graphTraversalOutboundLimit == that.graphTraversalOutboundLimit
           && Objects.equals(blacklistedSchemas, that.blacklistedSchemas)
           && Objects.equals(blacklistedTypes, that.blacklistedTypes)
           && Objects.equals(whitelistedSchemas, that.whitelistedSchemas)
           && Objects.equals(whitelistedTypes, that.whitelistedTypes)
           && nodeSizing == that.nodeSizing && nodeColouring == that.nodeColouring
           && aggressiveDiscovery == that.aggressiveDiscovery
           && layoutAlgorithm == that.layoutAlgorithm;
  }

  @Override
  public int hashCode() {
    return Objects.hash(graphTraversalInboundLimit, graphTraversalOutboundLimit, blacklistedSchemas,
        blacklistedTypes, whitelistedSchemas, whitelistedTypes, nodeSizing, nodeColouring,
        aggressiveDiscovery, layoutAlgorithm);
  }

  @Override
  public String toString() {
    return "GraphSettings {" +
           "\n  graphTraversalInboundLimit: " + graphTraversalInboundLimit +
           ",\n  graphTraversalOutboundLimit: " + graphTraversalOutboundLimit +
           ",\n  blacklistedSchemas: " + blacklistedSchemas +
           ",\n  blacklistedTypes: " + blacklistedTypes +
           ",\n  whitelistedSchemas: " + whitelistedSchemas +
           ",\n  whitelistedTypes: " + whitelistedTypes +
           ",\n  nodeSizing: " + nodeSizing +
           ",\n  nodeColouring: " + nodeColouring +
           ",\n  aggressiveDiscovery: " + aggressiveDiscovery +
           ",\n  layoutAlgorithm: " + layoutAlgorithm +
           "\n}";
  }

}
