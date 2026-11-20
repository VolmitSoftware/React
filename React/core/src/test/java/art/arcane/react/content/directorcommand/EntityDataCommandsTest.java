package art.arcane.react.content.directorcommand;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EntityDataCommandsTest {
  @Test
  void sendsPriorityCrowdingNearestPlayerAndAge() {
    RecordingSender sender = new RecordingSender();

    EntityDataCommands.send(sender, 100.7D, 1.2D, 0.55D, 2_000L);

    Assertions.assertEquals(4, sender.lines.size());
    Assertions.assertTrue(sender.lines.get(0).matches("Priority:\\s*100"), sender.lines.get(0));
    Assertions.assertTrue(sender.lines.get(1).matches("Crowding:\\s*1"), sender.lines.get(1));
    Assertions.assertTrue(sender.lines.get(2).startsWith("Nearest player:"), sender.lines.get(2));
    Assertions.assertTrue(sender.lines.get(3).startsWith("Updated:") && sender.lines.get(3).endsWith("ago"), sender.lines.get(3));
  }
}
