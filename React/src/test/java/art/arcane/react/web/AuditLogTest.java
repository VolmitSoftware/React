package art.arcane.react.web;

import art.arcane.react.api.web.AuditLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuditLogTest {

  @TempDir
  File tmpDir;

  private AuditLog synchronousLog() {
    return new AuditLog(tmpDir) {
      @Override
      protected void schedule(Runnable r) {
        r.run();
      }
    };
  }

  @Test
  void appendThenTailReturnsLineWithFields() {
    AuditLog log = synchronousLog();
    log.append("console", "pair", "label=laptop", "OK");
    List<String> tail = log.tail(1);
    assertEquals(1, tail.size());
    String line = tail.get(0);
    assertTrue(line.contains("\"actor\""), "Line must contain actor field: " + line);
    assertTrue(line.contains("console"), "Line must contain actor value: " + line);
    assertTrue(line.contains("\"op\""), "Line must contain op field: " + line);
    assertTrue(line.contains("pair"), "Line must contain op value: " + line);
    assertTrue(line.contains("\"detail\""), "Line must contain detail field: " + line);
    assertTrue(line.contains("label=laptop"), "Line must contain detail value: " + line);
    assertTrue(line.contains("\"result\""), "Line must contain result field: " + line);
    assertTrue(line.contains("\"OK\""), "Line must contain result value: " + line);
  }

  @Test
  void tailNReturnsLastNLines() {
    AuditLog log = synchronousLog();
    log.append("admin", "pair", "label=a", "OK");
    log.append("admin", "list", "", "OK");
    log.append("admin", "revoke", "id=x", "OK");
    List<String> tail = log.tail(2);
    assertEquals(2, tail.size());
    assertTrue(tail.get(0).contains("list"), "First of last-2 should be list: " + tail.get(0));
    assertTrue(tail.get(1).contains("revoke"), "Second of last-2 should be revoke: " + tail.get(1));
  }

  @Test
  void tailOnEmptyLogReturnsEmptyList() {
    AuditLog log = synchronousLog();
    List<String> tail = log.tail(5);
    assertEquals(0, tail.size());
  }

  @Test
  void tailLargerThanFileSizeReturnsAll() {
    AuditLog log = synchronousLog();
    log.append("a", "op1", "d1", "r1");
    log.append("b", "op2", "d2", "r2");
    List<String> tail = log.tail(100);
    assertEquals(2, tail.size());
  }
}
