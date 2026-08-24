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

import art.arcane.react.React;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.BenchmarkMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BenchmarkRunner {
  private static final AtomicBoolean RUNNING = new AtomicBoolean();

  private BenchmarkRunner() {
  }

  public static void run(VolmitSender sender, Set<BenchmarkTarget> targets) {
    if (targets.isEmpty()) {
      return;
    }

    if (!RUNNING.compareAndSet(false, true)) {
      ReactLanguage.send(sender, BenchmarkMessages.BUSY);
      return;
    }

    try {
      J.a(() -> {
        try {
          ReactLanguage.send(sender, BenchmarkMessages.HEADER);
          for (BenchmarkTarget target : EnumSet.copyOf(targets)) {
            execute(sender, target);
          }
          ReactLanguage.send(sender, BenchmarkMessages.SCALE);
        } finally {
          RUNNING.set(false);
        }
      });
    } catch (Throwable dispatchFailure) {
      RUNNING.set(false);
      throw dispatchFailure;
    }
  }

  public static BenchmarkResult processor(BenchmarkProfile profile) {
    CpuMeasurement measurement = BenchmarkEngine.measureCpu(profile);
    int integerScore = BenchmarkScale.higherIsBetter(measurement.integerMops(), BenchmarkScale.CPU_INTEGER_MOPS);
    int floatingScore = BenchmarkScale.higherIsBetter(measurement.floatingMops(), BenchmarkScale.CPU_FLOATING_MOPS);
    int cacheScore = BenchmarkScale.higherIsBetter(measurement.cacheMops(), BenchmarkScale.CPU_CACHE_MOPS);
    int multiCoreScore = BenchmarkScale.higherIsBetter(measurement.multiCoreMops(), BenchmarkScale.CPU_MULTI_CORE_MOPS);

    List<BenchmarkMetric> metrics = new ArrayList<>();
    metrics.add(BenchmarkMetric.detail(BenchmarkMessages.LABEL_THREADS, Integer.toString(measurement.threads())));
    metrics.add(BenchmarkMetric.detail(BenchmarkMessages.LABEL_SAMPLE, sample(profile)));
    metrics.add(BenchmarkMetric.scored(BenchmarkMessages.LABEL_CPU_INTEGER, operations(measurement.integerMops()), integerScore));
    metrics.add(BenchmarkMetric.scored(BenchmarkMessages.LABEL_CPU_FLOATING, operations(measurement.floatingMops()), floatingScore));
    metrics.add(BenchmarkMetric.scored(BenchmarkMessages.LABEL_CPU_CACHE, operations(measurement.cacheMops()), cacheScore));
    metrics.add(BenchmarkMetric.scored(BenchmarkMessages.LABEL_CPU_MULTI_CORE, operations(measurement.multiCoreMops()), multiCoreScore));
    metrics.add(BenchmarkMetric.detail(BenchmarkMessages.LABEL_CPU_SCALING, Form.f(measurement.scaling(), 2) + "x"));

    return new BenchmarkResult(
        BenchmarkMessages.NAME_CPU,
        metrics,
        BenchmarkScale.blend(integerScore, floatingScore, cacheScore, multiCoreScore),
        measurement.elapsedMillis()
    );
  }

  public static BenchmarkResult memory(BenchmarkProfile profile) {
    MemoryMeasurement measurement = BenchmarkEngine.measureMemory(profile);
    int writeScore = BenchmarkScale.higherIsBetter(measurement.writeGigabytesPerSecond(), BenchmarkScale.MEMORY_WRITE_GIGABYTES);
    int readScore = BenchmarkScale.higherIsBetter(measurement.readGigabytesPerSecond(), BenchmarkScale.MEMORY_READ_GIGABYTES);
    int copyScore = BenchmarkScale.higherIsBetter(measurement.copyGigabytesPerSecond(), BenchmarkScale.MEMORY_COPY_GIGABYTES);
    int latencyScore = BenchmarkScale.lowerIsBetter(measurement.randomAccessNanos(), BenchmarkScale.MEMORY_LATENCY_NANOS);

    List<BenchmarkMetric> metrics = new ArrayList<>();
    metrics.add(BenchmarkMetric.detail(BenchmarkMessages.LABEL_MEMORY_WORKING_SET, Form.memSize(measurement.workingSetBytes())));
    metrics.add(BenchmarkMetric.detail(BenchmarkMessages.LABEL_SAMPLE, sample(profile)));
    metrics.add(BenchmarkMetric.scored(BenchmarkMessages.LABEL_MEMORY_WRITE, gigabytes(measurement.writeGigabytesPerSecond()), writeScore));
    metrics.add(BenchmarkMetric.scored(BenchmarkMessages.LABEL_MEMORY_READ, gigabytes(measurement.readGigabytesPerSecond()), readScore));
    metrics.add(BenchmarkMetric.scored(BenchmarkMessages.LABEL_MEMORY_COPY, gigabytes(measurement.copyGigabytesPerSecond()), copyScore));
    metrics.add(BenchmarkMetric.scored(BenchmarkMessages.LABEL_MEMORY_LATENCY, Form.f(measurement.randomAccessNanos(), 1) + " ns", latencyScore));

    return new BenchmarkResult(
        BenchmarkMessages.NAME_MEMORY,
        metrics,
        BenchmarkScale.blend(writeScore, readScore, copyScore, latencyScore),
        measurement.elapsedMillis()
    );
  }

  public static BenchmarkResult drive(Path directory, DriveProfile profile) throws Exception {
    DriveMeasurement measurement = BenchmarkEngine.measureDrive(directory, profile);
    int writeScore = BenchmarkScale.higherIsBetter(measurement.writeMegabytesPerSecond(), BenchmarkScale.DRIVE_WRITE_MEGABYTES);
    int flushScore = BenchmarkScale.lowerIsBetter(measurement.flushMillis(), BenchmarkScale.DRIVE_FLUSH_MILLIS);

    List<BenchmarkMetric> metrics = new ArrayList<>();
    metrics.add(BenchmarkMetric.detail(BenchmarkMessages.LABEL_DRIVE_TARGET, measurement.target()));
    metrics.add(BenchmarkMetric.detail(BenchmarkMessages.LABEL_SAMPLE, Form.memSize(measurement.payloadBytes())));
    metrics.add(BenchmarkMetric.scored(BenchmarkMessages.LABEL_DRIVE_WRITE, megabytes(measurement.writeMegabytesPerSecond()), writeScore));
    metrics.add(BenchmarkMetric.scored(BenchmarkMessages.LABEL_DRIVE_FLUSH, Form.fd(measurement.flushMillis(), 2) + " ms", flushScore));
    metrics.add(BenchmarkMetric.detail(BenchmarkMessages.LABEL_DRIVE_READ, megabytes(measurement.readMegabytesPerSecond())));
    metrics.add(BenchmarkMetric.detail(BenchmarkMessages.LABEL_DRIVE_RANDOM_READ, Form.f(measurement.randomReadIops(), 0) + " IOPS"));

    return new BenchmarkResult(
        BenchmarkMessages.NAME_DRIVE,
        metrics,
        BenchmarkScale.blend(writeScore, flushScore),
        measurement.elapsedMillis()
    );
  }

  private static void execute(VolmitSender sender, BenchmarkTarget target) {
    TextKey name = target.message();
    ReactLanguage.send(sender, BenchmarkMessages.RUNNING, MessageArgument.untrusted("name", ReactLanguage.plain(name)));

    try {
      BenchmarkReport.send(sender, measure(target));
    } catch (Throwable failure) {
      React.reportError("Benchmark " + target.name() + " failed", failure);
      ReactLanguage.send(
          sender,
          BenchmarkMessages.FAILED,
          MessageArgument.untrusted("name", ReactLanguage.plain(name)),
          MessageArgument.untrusted("reason", String.valueOf(failure.getMessage()))
      );
    }
  }

  private static BenchmarkResult measure(BenchmarkTarget target) throws Exception {
    return switch (target) {
      case PROCESSOR -> processor(BenchmarkProfile.standard());
      case MEMORY -> memory(BenchmarkProfile.standard());
      case DRIVE -> drive(driveDirectory(), DriveProfile.standard());
    };
  }

  private static Path driveDirectory() {
    return React.instance.getDataFolder().toPath().resolve("benchmark");
  }

  private static String sample(BenchmarkProfile profile) {
    return profile.rounds() + " x " + profile.sampleMillis() + " ms";
  }

  private static String operations(double megaOperationsPerSecond) {
    return Form.f(megaOperationsPerSecond, 1) + " Mop/s";
  }

  private static String gigabytes(double gigabytesPerSecond) {
    return Form.f(gigabytesPerSecond, 2) + " GB/s";
  }

  private static String megabytes(double megabytesPerSecond) {
    return Form.f(megabytesPerSecond, 1) + " MB/s";
  }
}
