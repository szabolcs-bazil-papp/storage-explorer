package com.aestallon.storageexplorer.client.userconfig.model;

public class UmlExportSettings {

  private boolean detailComplexProps = true;

  private boolean drawEdgesFromProperty = false;

  private boolean labelEdges = true;

  private boolean displayArity = false;

  public boolean isDetailComplexProps() {
    return detailComplexProps;
  }

  public void setDetailComplexProps(boolean detailComplexProps) {
    this.detailComplexProps = detailComplexProps;
  }

  public boolean isDrawEdgesFromProperty() {
    return drawEdgesFromProperty;
  }

  public void setDrawEdgesFromProperty(boolean drawEdgesFromProperty) {
    this.drawEdgesFromProperty = drawEdgesFromProperty;
  }

  public boolean isLabelEdges() {
    return labelEdges;
  }

  public void setLabelEdges(boolean labelEdges) {
    this.labelEdges = labelEdges;
  }

  public boolean isDisplayArity() {
    return displayArity;
  }

  public void setDisplayArity(boolean displayArity) {
    this.displayArity = displayArity;
  }
}
