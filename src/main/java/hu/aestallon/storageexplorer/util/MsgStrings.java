package hu.aestallon.storageexplorer.util;

import org.smartbit4all.core.utility.StringConstant;
import com.google.common.base.Strings;

public final class MsgStrings {

  private MsgStrings() {}

  public static String trim(final String s, final int limit) {
    if (Strings.isNullOrEmpty(s)) {
      return StringConstant.EMPTY;
    }
    
    if (limit < 0) {
      return s;
    }
    
    if (limit < 3) {
      return s.length() > limit ? s.substring(0, limit) : s;
    }
    
    if (s.length() <= limit) {
      return s;
    }
    
    final String trunc = s.substring(0, limit - 3);
    return trunc + "...";
  }
}
