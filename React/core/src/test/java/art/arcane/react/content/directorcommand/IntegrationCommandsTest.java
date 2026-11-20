package art.arcane.react.content.directorcommand;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class IntegrationCommandsTest {
  private static final long NOW = 1_000_000L;

  @Test
  void neverAnsweredHeartbeatReadsNever() {
    Assertions.assertEquals("never", IntegrationCommands.heartbeatAge(0L, NOW));
    Assertions.assertEquals("never", IntegrationCommands.heartbeatAge(-5L, NOW));
  }

  @Test
  void heartbeatAgeEndsWithAgo() {
    String age = IntegrationCommands.heartbeatAge(NOW - 5_000L, NOW);

    Assertions.assertTrue(age.endsWith(" ago"), age);
    Assertions.assertNotEquals("never", age);
  }

  @Test
  void futureHeartbeatClampsToZeroAge() {
    Assertions.assertEquals(IntegrationCommands.heartbeatAge(NOW, NOW), IntegrationCommands.heartbeatAge(NOW + 9_000L, NOW));
  }

  @Test
  void emptyStatusSendsOnlyTheHeader() {
    RecordingSender sender = new RecordingSender();

    IntegrationCommands.status(sender, List.of(), null, List.of(), NOW);

    Assertions.assertEquals(1, sender.lines.size());
    Assertions.assertTrue(sender.lines.get(0).endsWith("Integration Status"));
  }

  @Test
  void entriesFollowTheHeaderInOrderWithAllFields() {
    RecordingSender sender = new RecordingSender();
    List<IntegrationCommands.Entry> entries = List.of(
        new IntegrationCommands.Entry("iris", "HEALTHY", "v3", 0L, "fine"),
        new IntegrationCommands.Entry("adapt", "DEGRADED", "v2", NOW, "slow"));

    IntegrationCommands.status(sender, entries, null, List.of(), NOW);

    Assertions.assertEquals(3, sender.lines.size());
    Assertions.assertEquals("- iris health=HEALTHY protocol=v3 heartbeat=never detail=fine", sender.lines.get(1));
    Assertions.assertEquals("- adapt health=DEGRADED protocol=v2 heartbeat="
        + IntegrationCommands.heartbeatAge(NOW, NOW) + " detail=slow", sender.lines.get(2));
  }

  @Test
  void blankCorrelationIsOmittedAndPresentOneIsShown() {
    RecordingSender blank = new RecordingSender();
    RecordingSender present = new RecordingSender();

    IntegrationCommands.status(blank, List.of(), "   ", List.of(), NOW);
    IntegrationCommands.status(present, List.of(), "abc-123", List.of(), NOW);

    Assertions.assertEquals(1, blank.lines.size());
    Assertions.assertEquals(2, present.lines.size());
    Assertions.assertEquals("Correlation: abc-123", present.lines.get(1));
  }

  @Test
  void timelineComesLastWithHeaderThenLinesInOrder() {
    RecordingSender sender = new RecordingSender();

    IntegrationCommands.status(sender, List.of(), "corr", List.of("first", "second"), NOW);

    Assertions.assertEquals(List.of("Correlation: corr", "Incident Timeline:", "first", "second"),
        sender.lines.subList(1, sender.lines.size()).stream().map(String::strip).toList());
  }
}
