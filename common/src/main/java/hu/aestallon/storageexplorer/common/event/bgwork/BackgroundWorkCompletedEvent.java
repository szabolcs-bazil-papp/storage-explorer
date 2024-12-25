package hu.aestallon.storageexplorer.common.event.bgwork;

public record BackgroundWorkCompletedEvent(
    BackgroundWorkCompletedEvent.BackgroundWorkResult result) {
  public enum BackgroundWorkResult { OK, ERR }

  public static BackgroundWorkCompletedEvent ok() {
    return new BackgroundWorkCompletedEvent(BackgroundWorkResult.OK);
  }

  public static BackgroundWorkCompletedEvent err() {
    return new BackgroundWorkCompletedEvent(BackgroundWorkResult.ERR);
  }

}
