package art.arcane.react.content.directorcommand;

import art.arcane.react.core.bridge.BridgeHealthReport;
import art.arcane.react.core.bridge.BridgeHealthReport.BridgeHealthEntry;
import art.arcane.react.core.bridge.BridgeKind;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class BridgeCommandsTest {
  private static BridgeHealthEntry entry(String id, boolean available, String summary, String failure) {
    return new BridgeHealthEntry(id, BridgeKind.METHOD, available, summary, failure);
  }

  @Test
  void emptyReportSendsOnlyTheNoneLine() {
    RecordingSender sender = new RecordingSender();

    BridgeCommands.status(sender, new BridgeHealthReport(List.of()));

    Assertions.assertEquals(1, sender.lines.size());
    Assertions.assertTrue(sender.lines.get(0).endsWith("No bridges registered."));
  }

  @Test
  void summaryComesFirstWithCountsThenEntriesInOrder() {
    RecordingSender sender = new RecordingSender();
    BridgeHealthReport report = new BridgeHealthReport(List.of(
        entry("chunk.load", true, "resolved v1", null),
        entry("entity.tick", false, null, "missing method"),
        entry("world.save", true, "resolved v2", null)));

    BridgeCommands.status(sender, report);

    Assertions.assertEquals(4, sender.lines.size());
    Assertions.assertTrue(sender.lines.get(0).endsWith("NMS Bridge Status - 2 available, 1 unavailable"));
    Assertions.assertEquals("  OK chunk.load resolved v1", sender.lines.get(1));
    Assertions.assertEquals("  FAIL entity.tick missing method", sender.lines.get(2));
    Assertions.assertEquals("  OK world.save resolved v2", sender.lines.get(3));
  }

  @Test
  void availableEntriesShowSummaryAndUnavailableShowFailure() {
    RecordingSender sender = new RecordingSender();
    BridgeHealthReport report = new BridgeHealthReport(List.of(
        entry("a", true, "ok-summary", "ignored-failure"),
        entry("b", false, "ignored-summary", "bad-failure")));

    BridgeCommands.status(sender, report);

    Assertions.assertEquals("  OK a ok-summary", sender.lines.get(1));
    Assertions.assertEquals("  FAIL b bad-failure", sender.lines.get(2));
  }

  @Test
  void allUnavailableReportsZeroAvailable() {
    RecordingSender sender = new RecordingSender();

    BridgeCommands.status(sender, new BridgeHealthReport(List.of(entry("x", false, "", "boom"))));

    Assertions.assertTrue(sender.lines.get(0).endsWith("NMS Bridge Status - 0 available, 1 unavailable"));
  }
}
