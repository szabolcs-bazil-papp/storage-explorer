package hu.aestallon.storageexplorer.event.msg;

public record ErrorMsg(String title, String message) implements Msg {
}
