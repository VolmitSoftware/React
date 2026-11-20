package art.arcane.react.content.directorcommand;

import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.platform.CommandHost.DistanceKind;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DistanceCommandsTest {
  private final List<MessageKey> keys = new ArrayList<>();
  private final List<Map<String, Object>> arguments = new ArrayList<>();
  private final DistanceCommands.Reply reply = (MessageKey key, MessageArgument... values) -> {
    keys.add(key);
    Map<String, Object> named = new LinkedHashMap<>();
    for (MessageArgument value : values) {
      named.put(value.name(), value.value());
    }
    arguments.add(named);
  };

  @Test
  void worldDistancesAcceptTheStandardRangeAndInheritOnlyForSend() {
    Assertions.assertTrue(DistanceCommands.isValidWorldDistance(DistanceKind.VIEW, 2));
    Assertions.assertTrue(DistanceCommands.isValidWorldDistance(DistanceKind.SIMULATION, 32));
    Assertions.assertTrue(DistanceCommands.isValidWorldDistance(DistanceKind.SEND, -1));
    Assertions.assertFalse(DistanceCommands.isValidWorldDistance(DistanceKind.VIEW, -1));
    Assertions.assertFalse(DistanceCommands.isValidWorldDistance(DistanceKind.SEND, 1));
    Assertions.assertFalse(DistanceCommands.isValidWorldDistance(DistanceKind.VIEW, 33));
    Assertions.assertThrows(NullPointerException.class, () -> DistanceCommands.isValidWorldDistance(null, 8));
  }

  @Test
  void playerDistancesAlwaysAllowInherit() {
    Assertions.assertTrue(DistanceCommands.isValidPlayerDistance(-1));
    Assertions.assertTrue(DistanceCommands.isValidPlayerDistance(2));
    Assertions.assertFalse(DistanceCommands.isValidPlayerDistance(1));
    Assertions.assertFalse(DistanceCommands.isValidPlayerDistance(33));
  }

  @Test
  void invalidReplyNamesTheAllowedRange() {
    DistanceCommands.invalid(reply, false);
    DistanceCommands.invalid(reply, true);

    Assertions.assertEquals(List.of(CommandMessages.DISTANCE_INVALID, CommandMessages.DISTANCE_INVALID), keys);
    Assertions.assertEquals("between 2 and 32 chunks", arguments.get(0).get("range"));
    Assertions.assertEquals("between 2 and 32 chunks, or -1 to inherit", arguments.get(1).get("range"));
  }

  @Test
  void setReplyShowsInheritAndTheTypeName() {
    DistanceCommands.set(reply, "player", "Steve", DistanceKind.SEND, -1);
    DistanceCommands.set(reply, "server", DistanceCommands.SERVER_TARGET, DistanceKind.SIMULATION, 10);

    Assertions.assertEquals(Map.of("scope", "player", "target", "Steve", "type", "send view", "distance", "inherit"),
        arguments.get(0));
    Assertions.assertEquals(Map.of("scope", "server", "target", "all loaded worlds", "type", "simulation", "distance", "10"),
        arguments.get(1));
  }

  @Test
  void unavailableAndFailedRepliesCarryScopeAndType() {
    DistanceCommands.unavailable(reply, "world", DistanceKind.SEND);
    DistanceCommands.failed(reply, "world", "nether", DistanceKind.VIEW);

    Assertions.assertEquals(List.of(CommandMessages.DISTANCE_UNAVAILABLE, CommandMessages.DISTANCE_FAILED), keys);
    Assertions.assertEquals(Map.of("scope", "world", "type", "send view"), arguments.get(0));
    Assertions.assertEquals(Map.of("scope", "world", "target", "nether", "type", "view"), arguments.get(1));
    Assertions.assertEquals("Failed to set world view distance for nether",
        DistanceCommands.failureContext("world", "nether", DistanceKind.VIEW));
  }
}
