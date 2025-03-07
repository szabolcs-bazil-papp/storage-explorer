package com.aestallon.storageexplorer.common.event.msg;

import com.aestallon.storageexplorer.common.util.MsgStrings;

public sealed interface Msg permits ErrorMsg, WarnMsg, InfoMsg {

  int LIMIT_TITLE = 40;
  int LIMIT_MESSAGE = 200;

  static InfoMsg info(final String title, final String message) {
    return new InfoMsg(
        MsgStrings.trim(title, LIMIT_TITLE),
        MsgStrings.trim(message, LIMIT_MESSAGE));
  }

  static WarnMsg warn(final String title, final String message) {
    return new WarnMsg(
        MsgStrings.trim(title, LIMIT_TITLE),
        MsgStrings.trim(message, LIMIT_MESSAGE));
  }

  static ErrorMsg err(final String title, final String message) {
    return new ErrorMsg(
        MsgStrings.trim(title, LIMIT_TITLE),
        MsgStrings.trim(message, LIMIT_MESSAGE));
  }

  String title();

  String message();

}
