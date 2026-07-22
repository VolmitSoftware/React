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
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import java.util.TreeMap;

public class DriveBenchmark implements Runnable {
  private VolmitSender sender;

  public DriveBenchmark(VolmitSender sender) {
    this.sender = sender;
  }

  public void benchmarkDrive() {
    J.a(this);
  }

  @Override
  public void run() {
    double score = doDriveBenchmark();
    String result = DriveResult.getSpeedLabel(score);
    ReactLanguage.send(
        sender,
        BenchmarkMessages.RESULT,
        MessageArgument.untrusted("rating", result),
        MessageArgument.untrusted("score", score)
    );
  }

  private double doDriveBenchmark() {
    ReactLanguage.send(sender, BenchmarkMessages.DRIVE_STARTING);

    // Create a temporary file
    File file;
    try {
      file = File.createTempFile("drive_benchmark", ".tmp");
    } catch (IOException e) {
      ReactLanguage.send(sender, BenchmarkMessages.DRIVE_TEMP_FAILED);
      return 0.0;
    }

    // Write random data to the temporary file
    Random random = new Random();
    byte[] buffer = new byte[1024];
    random.nextBytes(buffer);

    long startTime = System.nanoTime();

    // Perform read and write operations
    try {
      for (int i = 0; i < 1000; i++) {
        // Write data to the file
        Files.write(file.toPath(), buffer);

        // Read data from the file
        Files.readAllBytes(file.toPath());
      }
    } catch (IOException e) {
      ReactLanguage.send(sender, BenchmarkMessages.DRIVE_IO_FAILED);
      return 0.0;
    }

    long endTime = System.nanoTime();
    long duration = (endTime - startTime);

    ReactLanguage.send(sender, BenchmarkMessages.COMPLETE);

    // Calculate the score based on the duration
    return 1000000000.0 / (duration / 1000000.0);
  }

  private enum DriveResult {
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

    DriveResult(TextKey message) {
      this.message = message;
    }

    public static String getSpeedLabel(double speed) {
      TreeMap<Double, DriveResult> speedMap = new TreeMap<>();
      speedMap.put(200000.0, INSANELY_FAST);
      speedMap.put(150000.0, ULTRA_FAST);
      speedMap.put(100000.0, VERY_FAST);
      speedMap.put(50000.0, FAST);
      speedMap.put(25000.0, GOOD);
      speedMap.put(10000.0, AVERAGE);
      speedMap.put(5000.0, SLOW);
      speedMap.put(1000.0, VERY_SLOW);

      for (double speedThreshold : speedMap.descendingKeySet()) {
        if (speed > speedThreshold) {
          return ReactLanguage.plain(speedMap.get(speedThreshold).message);
        }
      }

      return ReactLanguage.plain(ULTRA_SLOW.message);
    }
  }
}
