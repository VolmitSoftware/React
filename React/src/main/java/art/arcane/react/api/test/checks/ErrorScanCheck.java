package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.test.ReactAsyncSubsystemCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.api.test.TestStatus;
import art.arcane.react.util.common.scheduling.J;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ErrorScanCheck implements ReactAsyncSubsystemCheck {
  private static final String SUBSYSTEM = "errors";
  private static final long MAX_LOG_TAIL_BYTES = 1_048_576L;

  @Override
  public String subsystem() {
    return SUBSYSTEM;
  }

  @Override
  public void run(TestReport report, Runnable onDone) {
    AtomicBoolean completed = new AtomicBoolean(false);
    Runnable complete = () -> {
      if (completed.compareAndSet(false, true)) {
        onDone.run();
      }
    };

    long startedAt = report.startedAtMillis();
    long scanUntil = System.currentTimeMillis();

    try {
      J.a(() -> {
        LogScanResult scan;
        try {
          scan = scanLog(startedAt, scanUntil);
        } catch (Throwable scanError) {
          scan = LogScanResult.unavailable(describeLogPath(), "scan failed: " + describe(scanError));
        }

        LogScanResult finalScan = scan;
        J.s(() -> {
          try {
            recordCounter(report, startedAt);
            recordLogScan(report, finalScan);
          } catch (Throwable recordError) {
            report.record(SUBSYSTEM, "exception", TestStatus.WARN, describe(recordError), null);
          } finally {
            complete.run();
          }
        });
      });
    } catch (Throwable scheduleError) {
      try {
        recordCounter(report, startedAt);
        report.record(SUBSYSTEM, "log-scan", TestStatus.INFO,
            "Async log scan could not be scheduled: " + describe(scheduleError), null);
      } finally {
        complete.run();
      }
    }
  }

  private void recordCounter(TestReport report, long startedAt) {
    int sinceRunStart = React.reportedErrorsSince(startedAt);
    int totalSinceStartup = React.reportedErrorCount();

    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("sinceRunStart", sinceRunStart);
    data.put("totalSinceStartup", totalSinceStartup);

    if (sinceRunStart > 0) {
      report.record(SUBSYSTEM, "reported-errors", TestStatus.FAIL,
          sinceRunStart + " React.reportError call(s) during this run (" + totalSinceStartup + " total since startup)", data);
    } else {
      report.record(SUBSYSTEM, "reported-errors", TestStatus.PASS,
          "No React.reportError calls during this run (" + totalSinceStartup + " total since startup)", data);
    }
  }

  private void recordLogScan(TestReport report, LogScanResult scan) {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("logPath", scan.path());
    data.put("available", scan.logAvailable());
    data.put("linesScanned", scan.totalScanned());
    data.put("reactErrorLines", scan.reactErrorLines());
    data.put("tickCrashLines", scan.tickCrashLines());
    if (scan.note() != null) {
      data.put("note", scan.note());
    }

    if (!scan.logAvailable()) {
      report.record(SUBSYSTEM, "log-scan", TestStatus.INFO,
          "Server log not scanned (" + scan.note() + "); relying on error counter", data);
      return;
    }

    if (scan.crossedMidnight()) {
      report.record(SUBSYSTEM, "log-scan", TestStatus.INFO,
          "Run window crossed midnight; skipped time-filtered log scan", data);
      return;
    }

    if (scan.tickCrashLines() > 0 || scan.reactErrorLines() > 0) {
      report.record(SUBSYSTEM, "log-scan", TestStatus.FAIL,
          scan.tickCrashLines() + " tick crash + " + scan.reactErrorLines()
              + " React error line(s) in latest.log during run", data);
      return;
    }

    report.record(SUBSYSTEM, "log-scan", TestStatus.PASS,
        "No React error/crash lines in latest.log during run (" + scan.totalScanned() + " lines scanned)", data);
  }

  private LogScanResult scanLog(long startedAt, long scanUntil) throws IOException {
    File logFile = serverLogFile();
    String path = logFile == null ? "<unknown>" : logFile.getAbsolutePath();

    if (logFile == null || !logFile.isFile() || !logFile.canRead()) {
      return LogScanResult.unavailable(path, "file missing or unreadable");
    }

    ZoneId zone = ZoneId.systemDefault();
    LocalTime startTime = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalTime();
    LocalTime endTime = Instant.ofEpochMilli(scanUntil).atZone(zone).toLocalTime();
    boolean crossedMidnight = endTime.isBefore(startTime);

    List<String> lines = readTail(logFile, MAX_LOG_TAIL_BYTES);
    int scanned = 0;
    int reactErrors = 0;
    int tickCrashes = 0;

    for (String rawLine : lines) {
      String line = stripTrailingReturn(rawLine);
      if (line.isEmpty()) {
        continue;
      }
      scanned++;

      if (crossedMidnight) {
        continue;
      }

      LocalTime lineTime = parseLineTime(line);
      if (lineTime == null) {
        continue;
      }
      if (lineTime.isBefore(startTime) || lineTime.isAfter(endTime)) {
        continue;
      }

      if (isTickCrashLine(line)) {
        tickCrashes++;
        continue;
      }
      if (isReactErrorLine(line)) {
        reactErrors++;
      }
    }

    return new LogScanResult(true, path, crossedMidnight, scanned, reactErrors, tickCrashes, null);
  }

  private File serverLogFile() {
    React instance = React.instance;
    if (instance == null) {
      return null;
    }
    File dataFolder = instance.getDataFolder();
    if (dataFolder == null) {
      return null;
    }
    File pluginsFolder = dataFolder.getParentFile();
    if (pluginsFolder == null) {
      return null;
    }
    File serverRoot = pluginsFolder.getParentFile();
    if (serverRoot == null) {
      return null;
    }
    return new File(new File(serverRoot, "logs"), "latest.log");
  }

  private String describeLogPath() {
    File logFile = serverLogFile();
    return logFile == null ? "<unknown>" : logFile.getAbsolutePath();
  }

  private List<String> readTail(File file, long maxBytes) throws IOException {
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      long length = raf.length();
      if (length <= 0L) {
        return new ArrayList<String>();
      }
      long start = Math.max(0L, length - maxBytes);
      raf.seek(start);
      int size = (int) (length - start);
      byte[] buffer = new byte[size];
      raf.readFully(buffer);
      String content = new String(buffer, StandardCharsets.UTF_8);
      String[] split = content.split("\n", -1);
      List<String> lines = new ArrayList<String>(split.length);
      int from = start > 0L ? 1 : 0;
      for (int i = from; i < split.length; i++) {
        lines.add(split[i]);
      }
      return lines;
    }
  }

  private String stripTrailingReturn(String line) {
    if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
      return line.substring(0, line.length() - 1);
    }
    return line;
  }

  private LocalTime parseLineTime(String line) {
    if (line.length() < 9 || line.charAt(0) != '[' || line.charAt(3) != ':' || line.charAt(6) != ':') {
      return null;
    }
    int hour = parseTwoDigits(line, 1);
    int minute = parseTwoDigits(line, 4);
    int second = parseTwoDigits(line, 7);
    if (hour < 0 || minute < 0 || second < 0 || hour > 23 || minute > 59 || second > 59) {
      return null;
    }
    return LocalTime.of(hour, minute, second);
  }

  private int parseTwoDigits(String line, int index) {
    if (index + 1 >= line.length()) {
      return -1;
    }
    char high = line.charAt(index);
    char low = line.charAt(index + 1);
    if (high < '0' || high > '9' || low < '0' || low > '9') {
      return -1;
    }
    return ((high - '0') * 10) + (low - '0');
  }

  private boolean isTickCrashLine(String line) {
    return line.contains("Tick task crashed");
  }

  private boolean isReactErrorLine(String line) {
    boolean reactAttributed = line.contains("art.arcane.react") || line.contains("[React]");
    if (!reactAttributed) {
      return false;
    }
    return line.contains("ERROR") || line.contains("SEVERE") || line.contains("FATAL");
  }

  private String describe(Throwable throwable) {
    return throwable.getClass().getSimpleName()
        + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
  }

  private record LogScanResult(boolean logAvailable, String path, boolean crossedMidnight, int totalScanned,
                               int reactErrorLines, int tickCrashLines, String note) {
    private static LogScanResult unavailable(String path, String note) {
      return new LogScanResult(false, path, false, 0, 0, 0, note);
    }
  }
}
