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

import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class CPUBenchmark implements Runnable {
  private CommandSender sender;

  public CPUBenchmark(CommandSender sender) {
    this.sender = sender;
  }

  public void BenchmarkCPU() {
    J.a(this);
  }

  @Override
  public void run() {
    int score = doCPUBenchmark();
    String result = CPUResult.getSpeedLabel(score);
    ReactLanguage.send(
        sender,
        BenchmarkMessages.CPU_RESULT,
        MessageArgument.untrusted("rating", result),
        MessageArgument.untrusted("milliseconds", score)
    );
  }

  private int doCPUBenchmark() {
    long startTime = System.nanoTime();
    ReactLanguage.send(sender, BenchmarkMessages.CPU_STARTING);

    // Arithmetic Operations
    long resultInt = 1;
    for (long i = 1; i <= 1000000; i++) {
      resultInt *= i;
      resultInt /= (i != 0 ? i : 1);
    }

    // Floating-Point Operations
    double resultDouble = 0.01;
    for (int i = 0; i < 1000000; i++) {
      resultDouble *= 1.000001;
      resultDouble /= 1.000001;
    }

    // Logical Operations
    int resultLogical = 0x55555555;
    for (int i = 0; i < 1000000; i++) {
      resultLogical = resultLogical & 0xAAAAAAAA;
      resultLogical = resultLogical | 0x55555555;
    }

    // Trigonometric Calculations
    double resultTrig = 0;
    for (int i = 0; i < 1000000; i++) {
      resultTrig = Math.sin(i) + Math.cos(i) + Math.tan(i);
    }

    long endTime = System.nanoTime();
    long duration = (endTime - startTime);

    ReactLanguage.send(sender, BenchmarkMessages.COMPLETE_LOWER_BETTER);
    return (int) (TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS));
  }

  private enum CPUResult {
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

    CPUResult(TextKey message) {
      this.message = message;
    }

    public static String getSpeedLabel(int s) {
      TreeMap<Integer, CPUResult> speedMap = new TreeMap<>();
      speedMap.put(10, INSANELY_FAST);
      speedMap.put(30, ULTRA_FAST);
      speedMap.put(50, VERY_FAST);
      speedMap.put(80, FAST);
      speedMap.put(100, GOOD);
      speedMap.put(150, AVERAGE);
      speedMap.put(200, SLOW);
      speedMap.put(400, VERY_SLOW);

      for (int speedThreshold : speedMap.descendingKeySet()) {
        if (s > speedThreshold) {
          return ReactLanguage.plain(speedMap.get(speedThreshold).message);
        }
      }

      return ReactLanguage.plain(ULTRA_SLOW.message);
    }
  }
}
