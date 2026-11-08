/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.core.service.fs;

import java.time.Instant;

/**
 * Timestamps in the format the platform writes ({@code OffsetDateTime} as ISO-8601).
 *
 * <p>Jackson prints the fraction of a second with as many digits as needed (none, 3, 6 or 9 for a
 * precise clock; the platform on Windows shows 7 because its clock ticks in 100 ns). This writes
 * UTC ({@code Z}) with the fraction trimmed of trailing zeros, which the platform reads back
 * without trouble.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code timestamp} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/timestamp.rs}).
 */
final class Timestamp {

  private Timestamp() {}

  /** The current time as the platform formats it. */
  static String now() {
    final Instant instant = Instant.now();
    return formatUtc(instant.getEpochSecond(), instant.getNano());
  }

  /**
   * {@code 1970-01-01T00:00:00Z} plus the given seconds; the fraction (nanoseconds) is printed
   * only if it is not zero, without trailing zeros.
   */
  static String formatUtc(final long epochSeconds, final int nanos) {
    final long days = Math.floorDiv(epochSeconds, 86_400L);
    final long secondsOfDay = Math.floorMod(epochSeconds, 86_400L);
    final long[] ymd = civilFromDays(days);
    final StringBuilder out = new StringBuilder(String.format("%04d-%02d-%02dT%02d:%02d:%02d",
        ymd[0], ymd[1], ymd[2], secondsOfDay / 3600, (secondsOfDay % 3600) / 60, secondsOfDay % 60));
    if (nanos != 0) {
      String fraction = String.format("%09d", nanos);
      int end = fraction.length();
      while (end > 0 && fraction.charAt(end - 1) == '0') {
        end--;
      }
      out.append('.').append(fraction, 0, end);
    }
    out.append('Z');
    return out.toString();
  }

  /**
   * Days since 1970-01-01 to {@code (year, month, day)} in the proleptic Gregorian calendar
   * (Howard Hinnant's {@code civil_from_days}).
   */
  private static long[] civilFromDays(final long days) {
    final long z = days + 719_468L;
    final long era = Math.floorDiv(z, 146_097L);
    final long dayOfEra = Math.floorMod(z, 146_097L);
    final long yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365;
    final long dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
    final long monthPhase = (5 * dayOfYear + 2) / 153;
    final long day = dayOfYear - (153 * monthPhase + 2) / 5 + 1;
    final long month = (monthPhase < 10) ? monthPhase + 3 : monthPhase - 9;
    final long year = yearOfEra + era * 400 + (month <= 2 ? 1 : 0);
    return new long[] {year, month, day};
  }
}
