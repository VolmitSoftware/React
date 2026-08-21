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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class BenchmarkEngine {
  private static final long GOLDEN = 0x9E3779B97F4A7C15L;
  private static final long MIX_A = 0xBF58476D1CE4E5B9L;
  private static final long MIX_B = 0x94D049BB133111EBL;
  private static final int BATCH = 4096;
  private static final long MEGA = 1_000_000L;
  private static final double NANOS_PER_SECOND = 1_000_000_000.0;
  private static final double BYTES_PER_GIGABYTE = 1_000_000_000.0;
  private static final double BYTES_PER_MEGABYTE = 1_000_000.0;

  private static volatile long INTEGER_SINK;
  private static volatile double FLOATING_SINK;

  private BenchmarkEngine() {
  }

  public static CpuMeasurement measureCpu(BenchmarkProfile profile) {
    long started = System.nanoTime();
    int[] chase = buildPointerChase(profile.workingSetBytes() / Integer.BYTES);

    double integerMops = bestOfKernel(profile, BenchmarkEngine::integerSteps);
    double floatingMops = bestOfKernel(profile, BenchmarkEngine::floatingSteps);
    double cacheMops = bestOfKernel(profile, deadline -> chaseSteps(deadline, chase));

    int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
    double multiCoreMops = threads == 1 ? integerMops : parallelIntegerThroughput(profile, threads);

    return new CpuMeasurement(
        integerMops,
        floatingMops,
        cacheMops,
        multiCoreMops,
        threads,
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
    );
  }

  public static MemoryMeasurement measureMemory(BenchmarkProfile profile) {
    long started = System.nanoTime();
    int elements = Math.max(8192, profile.workingSetBytes() / Long.BYTES);
    long payloadBytes = (long) elements * Long.BYTES;

    long allocationStarted = System.nanoTime();
    long[] source = new long[elements];
    long[] destination = new long[elements];
    long allocationNanos = System.nanoTime() - allocationStarted;

    long warmupDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(profile.warmupMillis());
    long warmupSeed = 1L;
    do {
      writePass(source, warmupSeed++);
      readPass(source);
      copyPass(source, destination);
    } while (System.nanoTime() < warmupDeadline);

    double writeGigabytes = 0.0;
    double readGigabytes = 0.0;
    double copyGigabytes = 0.0;
    for (int round = 0; round < profile.rounds(); round++) {
      long writeNanos = writePass(source, round + 2L);
      long readNanos = readPass(source);
      long copyNanos = copyPass(source, destination);
      writeGigabytes = Math.max(writeGigabytes, rate(payloadBytes, writeNanos, BYTES_PER_GIGABYTE));
      readGigabytes = Math.max(readGigabytes, rate(payloadBytes, readNanos, BYTES_PER_GIGABYTE));
      copyGigabytes = Math.max(copyGigabytes, rate(payloadBytes, copyNanos, BYTES_PER_GIGABYTE));
    }

    int[] chase = buildPointerChase(profile.workingSetBytes() / Integer.BYTES);
    double chaseMops = bestOfKernel(profile, deadline -> chaseSteps(deadline, chase));
    double randomAccessNanos = chaseMops <= 0.0 ? 0.0 : 1000.0 / chaseMops;

    INTEGER_SINK = destination[destination.length - 1];

    return new MemoryMeasurement(
        writeGigabytes,
        readGigabytes,
        copyGigabytes,
        randomAccessNanos,
        payloadBytes,
        allocationNanos,
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
    );
  }

  public static DriveMeasurement measureDrive(Path directory, DriveProfile profile) throws IOException {
    long started = System.nanoTime();
    Files.createDirectories(directory);
    Path file = directory.resolve("react-benchmark-" + System.nanoTime() + ".tmp");

    try {
      long payloadBytes = profile.alignedPayloadBytes();
      ByteBuffer chunk = ByteBuffer.allocateDirect(profile.chunkBytes());
      fillBuffer(chunk);

      long writeNanos = sequentialWrite(file, chunk, profile);
      double flushMillis = flushLatency(file, profile);
      long readNanos = sequentialRead(file, chunk, profile);
      long randomNanos = randomRead(file, payloadBytes, profile);

      double randomSeconds = randomNanos / NANOS_PER_SECOND;
      double randomReadIops = randomSeconds <= 0.0 ? 0.0 : profile.randomReads() / randomSeconds;

      return new DriveMeasurement(
          rate(payloadBytes, writeNanos, BYTES_PER_MEGABYTE),
          rate(payloadBytes, readNanos, BYTES_PER_MEGABYTE),
          randomReadIops,
          randomNanos / (profile.randomReads() * 1000.0),
          flushMillis,
          payloadBytes,
          directory.toAbsolutePath().toString(),
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
      );
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private static long sequentialWrite(Path file, ByteBuffer chunk, DriveProfile profile) throws IOException {
    try (FileChannel channel = FileChannel.open(
        file,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING
    )) {
      long start = System.nanoTime();
      for (int index = 0; index < profile.chunks(); index++) {
        chunk.clear();
        while (chunk.hasRemaining()) {
          channel.write(chunk);
        }
      }
      channel.force(true);
      return System.nanoTime() - start;
    }
  }

  private static double flushLatency(Path file, DriveProfile profile) throws IOException {
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
      ByteBuffer marker = ByteBuffer.allocateDirect(Long.BYTES);
      long total = 0L;
      for (int index = 0; index < profile.flushes(); index++) {
        marker.clear();
        marker.putLong(GOLDEN + index);
        marker.flip();
        channel.position((long) index * Long.BYTES);
        while (marker.hasRemaining()) {
          channel.write(marker);
        }
        long start = System.nanoTime();
        channel.force(false);
        total += System.nanoTime() - start;
      }
      return total / (profile.flushes() * 1_000_000.0);
    }
  }

  private static long sequentialRead(Path file, ByteBuffer chunk, DriveProfile profile) throws IOException {
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      long start = System.nanoTime();
      long checksum = 0L;
      for (int index = 0; index < profile.chunks(); index++) {
        chunk.clear();
        while (chunk.hasRemaining() && channel.read(chunk) >= 0) {
          checksum++;
        }
      }
      long elapsed = System.nanoTime() - start;
      INTEGER_SINK = checksum;
      return elapsed;
    }
  }

  private static long randomRead(Path file, long payloadBytes, DriveProfile profile) throws IOException {
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      ByteBuffer buffer = ByteBuffer.allocateDirect(profile.randomReadBytes());
      long span = Math.max(1L, payloadBytes - profile.randomReadBytes());
      long state = GOLDEN;
      long checksum = 0L;
      long start = System.nanoTime();
      for (int index = 0; index < profile.randomReads(); index++) {
        state = nextState(state);
        long position = Math.floorMod(state, span);
        buffer.clear();
        checksum += channel.read(buffer, position);
      }
      long elapsed = System.nanoTime() - start;
      INTEGER_SINK = checksum;
      return elapsed;
    }
  }

  private static void fillBuffer(ByteBuffer buffer) {
    long state = GOLDEN;
    buffer.clear();
    while (buffer.remaining() >= Long.BYTES) {
      state = nextState(state);
      buffer.putLong(state);
    }
    while (buffer.hasRemaining()) {
      buffer.put((byte) 0);
    }
    buffer.flip();
  }

  private static double bestOfKernel(BenchmarkProfile profile, KernelStep kernel) {
    if (profile.warmupMillis() > 0L) {
      throughput(profile.warmupMillis(), kernel);
    }
    double best = 0.0;
    for (int round = 0; round < profile.rounds(); round++) {
      best = Math.max(best, throughput(profile.sampleMillis(), kernel));
    }
    return best;
  }

  private static double throughput(long windowMillis, KernelStep kernel) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(windowMillis);
    long start = System.nanoTime();
    long steps = kernel.run(deadline);
    long elapsed = System.nanoTime() - start;
    if (elapsed <= 0L) {
      return 0.0;
    }
    return steps / (double) elapsed * NANOS_PER_SECOND / MEGA;
  }

  private static double parallelIntegerThroughput(BenchmarkProfile profile, int threads) {
    double best = 0.0;
    for (int round = 0; round < profile.rounds(); round++) {
      best = Math.max(best, parallelIntegerRound(profile, threads));
    }
    return best;
  }

  private static double parallelIntegerRound(BenchmarkProfile profile, int threads) {
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch begin = new CountDownLatch(1);
    AtomicLong steps = new AtomicLong();
    List<Thread> workers = new ArrayList<>(threads);
    long window = TimeUnit.MILLISECONDS.toNanos(profile.sampleMillis());

    for (int index = 0; index < threads; index++) {
      Thread worker = new Thread(() -> {
        ready.countDown();
        try {
          begin.await();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return;
        }
        steps.addAndGet(integerSteps(System.nanoTime() + window));
      }, "react-benchmark-cpu-" + index);
      worker.setDaemon(true);
      workers.add(worker);
      worker.start();
    }

    try {
      ready.await();
      long start = System.nanoTime();
      begin.countDown();
      for (Thread worker : workers) {
        worker.join();
      }
      long elapsed = System.nanoTime() - start;
      if (elapsed <= 0L) {
        return 0.0;
      }
      return steps.get() / (double) elapsed * NANOS_PER_SECOND / MEGA;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return 0.0;
    }
  }

  private static long integerSteps(long deadline) {
    long state = GOLDEN;
    long steps = 0L;
    while (System.nanoTime() < deadline) {
      for (int index = 0; index < BATCH; index++) {
        state = nextState(state);
      }
      steps += BATCH;
    }
    INTEGER_SINK = state;
    return steps;
  }

  private static long floatingSteps(long deadline) {
    double accumulator = 1.5;
    long steps = 0L;
    while (System.nanoTime() < deadline) {
      for (int index = 0; index < BATCH; index++) {
        accumulator = accumulator * 1.0000001 + 0.5;
        accumulator = Math.sqrt(accumulator) + accumulator * 0.25;
        accumulator = accumulator - Math.floor(accumulator) + 1.0;
      }
      steps += BATCH;
    }
    FLOATING_SINK = accumulator;
    return steps;
  }

  private static long chaseSteps(long deadline, int[] chase) {
    int cursor = 0;
    long steps = 0L;
    while (System.nanoTime() < deadline) {
      for (int index = 0; index < BATCH; index++) {
        cursor = chase[cursor];
      }
      steps += BATCH;
    }
    INTEGER_SINK = cursor;
    return steps;
  }

  private static long writePass(long[] buffer, long seed) {
    long start = System.nanoTime();
    for (int index = 0; index < buffer.length; index++) {
      buffer[index] = seed + index;
    }
    return System.nanoTime() - start;
  }

  private static long readPass(long[] buffer) {
    long start = System.nanoTime();
    long checksum = 0L;
    for (int index = 0; index < buffer.length; index++) {
      checksum += buffer[index];
    }
    long elapsed = System.nanoTime() - start;
    INTEGER_SINK = checksum;
    return elapsed;
  }

  private static long copyPass(long[] source, long[] destination) {
    long start = System.nanoTime();
    System.arraycopy(source, 0, destination, 0, source.length);
    return System.nanoTime() - start;
  }

  private static int[] buildPointerChase(int length) {
    int size = Math.max(1024, length);
    int[] order = new int[size];
    for (int index = 0; index < size; index++) {
      order[index] = index;
    }
    long state = GOLDEN;
    for (int index = size - 1; index > 0; index--) {
      state = nextState(state);
      int swap = (int) Math.floorMod(state, index + 1L);
      int carry = order[index];
      order[index] = order[swap];
      order[swap] = carry;
    }
    int[] chase = new int[size];
    for (int index = 0; index < size; index++) {
      chase[order[index]] = order[(index + 1) % size];
    }
    return chase;
  }

  private static long nextState(long state) {
    long next = state;
    next ^= next << 13;
    next ^= next >>> 7;
    next ^= next << 17;
    next += GOLDEN;
    next *= MIX_A;
    next ^= (next >>> 31) * MIX_B;
    return next;
  }

  private static double rate(long bytes, long nanos, double unit) {
    if (nanos <= 0L) {
      return 0.0;
    }
    return bytes / (nanos / NANOS_PER_SECOND) / unit;
  }

  private interface KernelStep {
    long run(long deadline);
  }
}
