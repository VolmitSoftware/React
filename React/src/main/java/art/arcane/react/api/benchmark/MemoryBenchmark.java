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
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.command.CommandSender;

import java.nio.ByteBuffer;
import java.util.TreeMap;

public class MemoryBenchmark implements Runnable {
  private CommandSender sender;

  public MemoryBenchmark(CommandSender sender) {
    this.sender = sender;
  }

  public void benchmarkMemory() {
    J.a(this);
  }

  @Override
  public void run() {
    int score = doMemoryBenchmark();
    String result = MemoryResult.getSpeedLabel(score);
    ReactLanguage.send(
        sender,
        BenchmarkMessages.RESULT,
        MessageArgument.untrusted("rating", result),
        MessageArgument.untrusted("score", score)
    );
  }

  private int doMemoryBenchmark() {
    ReactLanguage.send(sender, BenchmarkMessages.MEMORY_STARTING);

    long startTime = System.nanoTime();

    int bufferSize = 1024 * 1024; // 1MB buffer size
    ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

    for (int i = 0; i < bufferSize; i++) {
      buffer.put((byte) (i % 256));
    }

    long endTime = System.nanoTime();
    long duration = (endTime - startTime);

    ReactLanguage.send(sender, BenchmarkMessages.COMPLETE);
    return (int) (1000000000.0 / (duration / (double) bufferSize));
  }

  private enum MemoryResult {
    ULTRA_SLOW(BenchmarkMessages.SPEED_ULTRA_SLOW),
    VERY_SLOW(BenchmarkMessages.SPEED_VERY_SLOW),
    SLOW(BenchmarkMessages.SPEED_SLOW),
    AVERAGE(BenchmarkMessages.SPEED_AVERAGE),
    GOOD(BenchmarkMessages.SPEED_GOOD),
    FAST(BenchmarkMessages.SPEED_FAST),
    VERY_FAST(BenchmarkMessages.SPEED_VERY_FAST),
    ULTRA_FAST(BenchmarkMessages.SPEED_ULTRA_FAST),
    INSANELY_FAST(BenchmarkMessages.SPEED_INSANELY_FAST);

    private final TextKey message;

    MemoryResult(TextKey message) {
      this.message = message;
    }

    public static String getSpeedLabel(int speed) {
      TreeMap<Integer, MemoryResult> speedMap = new TreeMap<>();
      speedMap.put(500, INSANELY_FAST);
      speedMap.put(400, ULTRA_FAST);
      speedMap.put(300, VERY_FAST);
      speedMap.put(200, FAST);
      speedMap.put(150, GOOD);
      speedMap.put(100, AVERAGE);
      speedMap.put(75, SLOW);
      speedMap.put(50, VERY_SLOW);

      for (int speedThreshold : speedMap.descendingKeySet()) {
        if (speed > speedThreshold) {
          return ReactLanguage.plain(speedMap.get(speedThreshold).message);
        }
      }

      return ReactLanguage.plain(ULTRA_SLOW.message);
    }
  }
}
