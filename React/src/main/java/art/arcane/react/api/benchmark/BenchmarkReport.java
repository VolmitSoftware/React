/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.api.benchmark;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.BenchmarkMessages;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;

public final class BenchmarkReport {
  private static final int BAR_CELLS = 14;
  private static final int BAR_FULL_SCALE = 200;
  private static final char BAR_FILLED = '█';
  private static final char BAR_EMPTY = '░';

  private BenchmarkReport() {
  }

  public static void send(VolmitSender sender, BenchmarkResult result) {
    ReactLanguage.send(sender, BenchmarkMessages.SECTION, MessageArgument.untrusted("name", ReactLanguage.plain(result.name())));

    for (BenchmarkMetric metric : result.metrics()) {
      if (metric.scored()) {
        ReactLanguage.send(
            sender,
            BenchmarkMessages.LINE_SCORED,
            MessageArgument.untrusted("label", ReactLanguage.plain(metric.label())),
            MessageArgument.untrusted("value", metric.value()),
            MessageArgument.trusted("bar", bar(metric.score(), metric.rating())),
            MessageArgument.untrusted("score", metric.score()),
            MessageArgument.trusted("rating", rating(metric.rating()))
        );
        continue;
      }

      ReactLanguage.send(
          sender,
          BenchmarkMessages.LINE,
          MessageArgument.untrusted("label", ReactLanguage.plain(metric.label())),
          MessageArgument.untrusted("value", metric.value())
      );
    }

    ReactLanguage.send(
        sender,
        BenchmarkMessages.OVERALL,
        MessageArgument.untrusted("label", ReactLanguage.plain(BenchmarkMessages.LABEL_OVERALL)),
        MessageArgument.untrusted("score", result.score()),
        MessageArgument.trusted("rating", rating(result.rating())),
        MessageArgument.untrusted("duration", Form.duration(result.elapsedMillis(), 1))
    );
  }

  public static String bar(int score, BenchmarkRating rating) {
    int filled = (int) Math.round((double) score * BAR_CELLS / BAR_FULL_SCALE);
    if (filled < 0) {
      filled = 0;
    }
    if (filled > BAR_CELLS) {
      filled = BAR_CELLS;
    }
    StringBuilder output = new StringBuilder(BAR_CELLS + 32);
    output.append('<').append(rating.color()).append('>');
    output.append(String.valueOf(BAR_FILLED).repeat(filled));
    output.append("</").append(rating.color()).append('>');
    output.append("<dark_gray>");
    output.append(String.valueOf(BAR_EMPTY).repeat(BAR_CELLS - filled));
    output.append("</dark_gray>");
    return output.toString();
  }

  public static String rating(BenchmarkRating rating) {
    return "<" + rating.color() + ">" + sanitize(ReactLanguage.plain(rating.message())) + "</" + rating.color() + ">";
  }

  private static String sanitize(String value) {
    return value.replace('<', ' ').replace('>', ' ').replace('§', ' ');
  }
}
