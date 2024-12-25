package hu.aestallon.storageexplorer.common.event.msg;

public record ErrorMsg(String title, String message) implements Msg {
}
