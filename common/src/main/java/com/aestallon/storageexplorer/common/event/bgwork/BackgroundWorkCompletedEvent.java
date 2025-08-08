package com.aestallon.storageexplorer.common.event.bgwork;

import java.util.UUID;

public record BackgroundWorkCompletedEvent(
    UUID uuid,
    BackgroundWorkCompletedEvent.BackgroundWorkResult result) {
  public enum BackgroundWorkResult { OK, ERR }

  public static BackgroundWorkCompletedEvent ok(UUID uuid) {
    return new BackgroundWorkCompletedEvent(uuid, BackgroundWorkResult.OK);
  }

  public static BackgroundWorkCompletedEvent err(UUID uuid) {
    return new BackgroundWorkCompletedEvent(uuid, BackgroundWorkResult.ERR);
  }

}
