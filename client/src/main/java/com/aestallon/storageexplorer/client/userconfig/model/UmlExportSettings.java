package com.aestallon.storageexplorer.client.userconfig.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonPropertyOrder({
    "detailComplexProps",
    "drawEdgesFromProperty",
    "labelEdges",
    "displayArity",
})
@JsonTypeName("UmlExportSettings")
public class UmlExportSettings {

  @JsonProperty("detailComplexProps")
  private boolean detailComplexProps = false;

  private boolean drawEdgesFromProperty = false;

  private boolean labelEdges = true;

  private boolean displayArity = false;

  @JsonProperty("detailComplexProps")
  public boolean isDetailComplexProps() {
    return detailComplexProps;
  }

  @JsonProperty("detailComplexProps")
  public void setDetailComplexProps(boolean detailComplexProps) {
    this.detailComplexProps = detailComplexProps;
  }

  @JsonProperty("drawEdgesFromProperty")
  public boolean isDrawEdgesFromProperty() {
    return drawEdgesFromProperty;
  }

  @JsonProperty("drawEdgesFromProperty")
  public void setDrawEdgesFromProperty(boolean drawEdgesFromProperty) {
    this.drawEdgesFromProperty = drawEdgesFromProperty;
  }

  @JsonProperty("labelEdges")
  public boolean isLabelEdges() {
    return labelEdges;
  }

  @JsonProperty("labelEdges")
  public void setLabelEdges(boolean labelEdges) {
    this.labelEdges = labelEdges;
  }

  @JsonProperty("displayArity")
  public boolean isDisplayArity() {
    return displayArity;
  }

  @JsonProperty("displayArity")
  public void setDisplayArity(boolean displayArity) {
    this.displayArity = displayArity;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    UmlExportSettings that = (UmlExportSettings) o;
    return detailComplexProps == that.detailComplexProps
        && drawEdgesFromProperty == that.drawEdgesFromProperty && labelEdges == that.labelEdges
        && displayArity == that.displayArity;
  }

  @Override
  public int hashCode() {
    return Objects.hash(detailComplexProps, drawEdgesFromProperty, labelEdges, displayArity);
  }

  @Override
  public String toString() {
    return "UmlExportSettings {" +
        "\n  detailComplexProps: " + detailComplexProps +
        ",\n  drawEdgesFromProperty: " + drawEdgesFromProperty +
        ",\n  labelEdges: " + labelEdges +
        ",\n  displayArity: " + displayArity +
        "\n}";
  }

}
