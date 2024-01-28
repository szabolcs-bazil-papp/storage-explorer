/*
 * Copyright (C) 2024 it4all Hungary Kft.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package hu.aestallon.storageexplorer.util;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.binarydata.BinaryData;
import org.smartbit4all.core.io.utility.FileIO;
import org.smartbit4all.core.utility.StringConstant;
import org.springframework.util.StreamUtils;
import com.google.common.base.Function;
import com.google.common.base.Strings;

public final class IO {

  private static final Logger log = LoggerFactory.getLogger(IO.class);

  private static final String URI_REGEX = "(?:\"uri\":\")([^\"]+)(?:\")";
  private static final Pattern URI_PTRN = Pattern.compile(URI_REGEX);

  public static Optional<URI> findObjectUri(String s) {
    if (Strings.isNullOrEmpty(s)) {
      return Optional.empty();
    }

    final var matcher = URI_PTRN.matcher(s);
    return (matcher.find())
        ? Optional.of(URI.create(matcher.group(1)))
        : Optional.empty();
  }

  public static Function<String, Optional<URI>> findObjectUri() {
    return IO::findObjectUri;
  }

  public static String read(Path p) {
    List<BinaryData> binaryData = FileIO.readMultipart(p.toFile());
    if (binaryData.isEmpty()) {
      return StringConstant.EMPTY;
    }
    try (var in = binaryData.get(binaryData.size() - 1).inputStream()) {
      return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error(e.getMessage(), e);
      return StringConstant.EMPTY;
    }
  }

}
