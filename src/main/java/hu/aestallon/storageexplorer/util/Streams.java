package hu.aestallon.storageexplorer.util;

import java.util.Enumeration;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class Streams {

  private Streams() {}

  public static <E> Stream<E> enumerationToStream(Enumeration<E> e) {
    final Iterable<E> iterable = e::asIterator;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

}
