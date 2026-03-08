package com.aestallon.storageexplorer.swing.ui.graph.uml;

class TooltipData {

  public enum TypeMatchStatus { MATCH, MISMATCH, UNAVAILABLE }

  // --- Shared ---
  public final String structuralTypeName;

  // --- Type tooltip (propertyPath == null) ---
  public final String typeLevelDescription;

  // --- Property tooltip (propertyPath != null) ---
  public final String propertyPath;
  public final String propertyName;
  public final String nominalTypeName;           // nullable
  public final String propertyDescription;
  public final TypeMatchStatus typeMatchStatus;
  public final String structuralPropertyTypeName;
  public final String nominalPropertyTypeName;   // nullable
  public final String nominalPropertyTypeDescription; // nullable

  /** Type-level tooltip */
  public TooltipData(String structuralTypeName, String typeLevelDescription) {
    this.structuralTypeName = structuralTypeName;
    this.typeLevelDescription = typeLevelDescription;
    this.propertyPath = null;
    this.propertyName = null;
    this.nominalTypeName = null;
    this.propertyDescription = null;
    this.typeMatchStatus = null;
    this.structuralPropertyTypeName = null;
    this.nominalPropertyTypeName = null;
    this.nominalPropertyTypeDescription = null;
  }

  /** Property-level tooltip */
  public TooltipData(
      String structuralTypeName,
      String propertyPath,
      String propertyName,
      String nominalTypeName,
      String propertyDescription,
      TypeMatchStatus typeMatchStatus,
      String structuralPropertyTypeName,
      String nominalPropertyTypeName,
      String nominalPropertyTypeDescription
  ) {
    this.structuralTypeName = structuralTypeName;
    this.typeLevelDescription = null;
    this.propertyPath = propertyPath;
    this.propertyName = propertyName;
    this.nominalTypeName = nominalTypeName;
    this.propertyDescription = propertyDescription;
    this.typeMatchStatus = typeMatchStatus;
    this.structuralPropertyTypeName = structuralPropertyTypeName;
    this.nominalPropertyTypeName = nominalPropertyTypeName;
    this.nominalPropertyTypeDescription = nominalPropertyTypeDescription;
  }

  public boolean isTypeTooltip() {
    return propertyPath == null;
  }
}
