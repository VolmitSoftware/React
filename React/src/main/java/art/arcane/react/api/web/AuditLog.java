package art.arcane.react.api.web;

import art.arcane.react.util.common.scheduling.J;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AuditLog {

  private final File logFile;

  public AuditLog(File dataFolder) {
    File webDir = new File(dataFolder, "web");
    webDir.mkdirs();
    this.logFile = new File(webDir, "audit.log");
  }

  protected void schedule(Runnable r) {
    J.a(r);
  }

  public void append(String actor, String op, String detail, String result) {
    long ts = System.currentTimeMillis();
    String line = "{\"ts\":" + ts
        + ",\"actor\":\"" + escapeJson(actor) + "\""
        + ",\"op\":\"" + escapeJson(op) + "\""
        + ",\"detail\":\"" + escapeJson(detail) + "\""
        + ",\"result\":\"" + escapeJson(result) + "\"}";
    schedule(() -> writeLineSafe(line));
  }

  public List<String> tail(int n) {
    if (!logFile.exists()) {
      return new ArrayList<>();
    }
    try {
      List<String> allLines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
      int start = Math.max(0, allLines.size() - n);
      return new ArrayList<>(allLines.subList(start, allLines.size()));
    } catch (IOException e) {
      return new ArrayList<>();
    }
  }

  private void writeLineSafe(String line) {
    try (PrintWriter pw = new PrintWriter(
        new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8))) {
      pw.println(line);
    } catch (IOException ignored) {
    }
  }

  private static String escapeJson(String value) {
    StringBuilder escaped = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '\\' -> escaped.append("\\\\");
        case '"' -> escaped.append("\\\"");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < 0x20) {
            escaped.append(String.format("\\u%04x", (int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.toString();
  }
}
