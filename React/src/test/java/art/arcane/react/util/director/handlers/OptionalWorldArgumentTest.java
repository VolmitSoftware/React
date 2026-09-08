package art.arcane.react.util.director.handlers;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

class OptionalWorldArgumentTest {
  @Test
  void bareOverworldNameResolvesToItsNamespacedKey() {
    WorldTargetCommand command = new WorldTargetCommand();
    RecordingSender sender = new RecordingSender();

    DirectorExecutionResult result = run(command, sender, "target", "world=world");

    Assertions.assertTrue(result.isSuccess(), "world=world was rejected: " + sender.messages);
    Assertions.assertEquals("minecraft:overworld", command.received);
  }

  @Test
  void bareNetherNameResolvesThroughTheParameterAlias() {
    WorldTargetCommand command = new WorldTargetCommand();
    RecordingSender sender = new RecordingSender();

    DirectorExecutionResult result = run(command, sender, "target", "w=world_nether");

    Assertions.assertTrue(result.isSuccess(), "w=world_nether was rejected: " + sender.messages);
    Assertions.assertEquals("minecraft:the_nether", command.received);
  }

  @Test
  void qualifiedKeysAndTheAllSentinelStillResolve() {
    WorldTargetCommand qualified = new WorldTargetCommand();
    RecordingSender qualifiedSender = new RecordingSender();
    Assertions.assertTrue(run(qualified, qualifiedSender, "target", "world=minecraft:the_end").isSuccess(),
        "world=minecraft:the_end was rejected: " + qualifiedSender.messages);
    Assertions.assertEquals("minecraft:the_end", qualified.received);

    WorldTargetCommand explicitAll = new WorldTargetCommand();
    Assertions.assertTrue(run(explicitAll, new RecordingSender(), "target", "world=ALL").isSuccess());
    Assertions.assertEquals("ALL", explicitAll.received);

    WorldTargetCommand defaulted = new WorldTargetCommand();
    Assertions.assertTrue(run(defaulted, new RecordingSender(), "target").isSuccess());
    Assertions.assertEquals("ALL", defaulted.received);
  }

  @Test
  void unknownWorldNameReportsAHandledParseFailure() {
    WorldTargetCommand command = new WorldTargetCommand();
    RecordingSender sender = new RecordingSender();

    DirectorExecutionResult result = run(command, sender, "target", "world=nowhere");

    Assertions.assertFalse(result.isSuccess());
    Assertions.assertTrue(result.isHandled(), "A rejected value must not read as an unknown command");
    Assertions.assertNull(command.received);
    Assertions.assertTrue(String.join(" | ", sender.messages).contains("nowhere"),
        "Expected the rejected value in the operator feedback: " + sender.messages);
  }

  private static DirectorExecutionResult run(WorldTargetCommand command, RecordingSender sender, String... args) {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(command);
    List<World> worlds = List.of(
        world("world", NamespacedKey.minecraft("overworld")),
        world("world_nether", NamespacedKey.minecraft("the_nether")),
        world("world_the_end", NamespacedKey.minecraft("the_end"))
    );

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(worlds);
      return engine.execute(new DirectorInvocation(sender, "react", List.of(args)));
    }
  }

  private static World world(String name, NamespacedKey key) {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getName()).thenReturn(name);
    Mockito.when(world.getKey()).thenReturn(key);
    return world;
  }

  @Director(name = "worldtarget")
  static class WorldTargetCommand {
    private String received;

    @Director(name = "target")
    public void target(
        @Param(
            name = "world",
            description = "World targeted by this action",
            customHandler = OptionalWorldHandler.class,
            defaultValue = "ALL",
            aliases = {"w"}
        )
        String world
    ) {
      this.received = world;
    }
  }

  private static final class RecordingSender implements DirectorSender {
    private final List<String> messages = new ArrayList<>();

    @Override
    public String getName() {
      return "CONSOLE";
    }

    @Override
    public boolean isPlayer() {
      return false;
    }

    @Override
    public void sendMessage(String message) {
      messages.add(message);
    }
  }
}
