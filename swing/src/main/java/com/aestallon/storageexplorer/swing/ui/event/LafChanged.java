package com.aestallon.storageexplorer.swing.ui.event;

public record LafChanged(LafChanged.Laf laf) {

  public enum Laf { LIGHT, DARK }

}
