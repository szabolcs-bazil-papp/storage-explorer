package com.aestallon.storageexplorer.client.userconfig.event;

public record LafChanged(LafChanged.Laf laf) {

  public enum Laf { LIGHT, DARK }

}
