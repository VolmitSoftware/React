package art.arcane.react.content.directorcommand;

import art.arcane.react.api.action.Action;
import art.arcane.react.content.action.ActionCaptureProfile;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionIncidentPlaybook;
import art.arcane.react.content.action.ActionPrewarmCriticalChunks;
import art.arcane.react.content.action.ActionPurgeChunks;
import art.arcane.react.content.action.ActionPurgeEntities;
import art.arcane.react.content.action.ActionQuarantineHotChunks;
import art.arcane.react.content.action.ActionTrimEntitiesByAgePriority;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.project.config.ConfigLocalization;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * The {@code /react action} commands that run the same on every platform.
 */
final class ActionCommands {
  private ActionCommands() {
  }

  static void captureProfile(VolmitSender sender, int seconds) {
    Action<ActionCaptureProfile.Params> action = ReactComponents.action(ActionCaptureProfile.ID);
    if (action == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }
    action.create(action.getDefaultParams().setSeconds(seconds), sender).queue();
  }

  static void collectGarbage(VolmitSender sender) {
    Action<ActionCollectGarbage.Params> action = ReactComponents.action(ActionCollectGarbage.ID);
    if (action == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }
    action.create(action.getDefaultParams(), sender).queue();
  }

  static void purgeChunks(VolmitSender sender, String world) {
    Action<ActionPurgeChunks.Params> action = ReactComponents.action(ActionPurgeChunks.ID);
    if (action == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }
    ActionPurgeChunks.Params params = action.getDefaultParams();

    if (!world.equals("ALL")) {
      params.withWorld(worldKey(world));
    }

    action.create(params, sender).queue();
  }

  static void quarantineHotChunks(VolmitSender sender, int maxChunks, double minScore, String world) {
    Action<ActionQuarantineHotChunks.Params> action = ReactComponents.action(ActionQuarantineHotChunks.ID);
    if (action == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }
    ActionQuarantineHotChunks.Params params = action.getDefaultParams()
        .setMaxChunks(Math.max(1, Math.min(maxChunks, 256)))
        .setMinimumChunkScore(Math.max(0D, minScore));

    if (!world.equals("ALL")) {
      params.withWorld(worldKey(world));
    }

    action.create(params, sender).queue();
  }

  static void prewarmCriticalChunks(VolmitSender sender, int maxChunks, int neighborRadius, String world) {
    Action<ActionPrewarmCriticalChunks.Params> action = ReactComponents.action(ActionPrewarmCriticalChunks.ID);
    if (action == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }
    ActionPrewarmCriticalChunks.Params params = action.getDefaultParams()
        .setMaxChunks(Math.max(1, Math.min(maxChunks, 512)))
        .setNeighborRadius(Math.max(0, Math.min(neighborRadius, 4)));

    if (!world.equals("ALL")) {
      params.withWorld(worldKey(world));
    }

    action.create(params, sender).queue();
  }

  static void incidentPlaybook(VolmitSender sender, boolean includeGC, int tier, String world) {
    Action<ActionIncidentPlaybook.Params> action = ReactComponents.action(ActionIncidentPlaybook.ID);
    if (action == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }
    ActionIncidentPlaybook.Params params = action.getDefaultParams()
        .setIncludeGarbageCollection(includeGC)
        .setTierOverride(Math.max(-1, Math.min(2, tier)));

    if (!world.equals("ALL")) {
      params.setWorld(world.trim());
    }

    action.create(params, sender).queue();
  }

  private static String worldKey(String world) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    ChunkHost chunks = platform == null ? null : platform.chunkHost().orElse(null);
    if (chunks == null) {
      throw new NoSuchElementException("No world " + world);
    }
    ReactWorld resolved = chunks.world(world).orElseThrow();
    return chunks.worldKey(resolved);
  }

  static void audit(VolmitSender sender) {
    ActionController controller = ReactComponents.actions();
    if (controller == null || controller.getActions() == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }

    ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_AUDIT_HEADER);
    controller.getActions().all().stream()
        .sorted(Comparator.comparing(Action::getId))
        .forEach(action -> {
          String localizedDescription = ConfigLocalization.description(action.getClass());
          String summary = localizedDescription.isBlank()
              ? ReactLanguage.plain(CommandMessages.ACTION_NO_DESCRIPTION)
              : localizedDescription;
          String state = ReactLanguage.plain(
              action.isEnabled() ? CommandMessages.STATE_ENABLED : CommandMessages.STATE_DISABLED
          );
          ReactLanguage.send(
              sender,
              CommandMessages.ACTION_AUDIT_ENTRY,
              MessageArgument.untrusted("id", action.getId()),
              MessageArgument.untrusted("state", state),
              MessageArgument.untrusted("summary", summary)
          );
        });
  }

  static void purgeEntities(VolmitSender sender, int radius, String world) {
    Action<ActionPurgeEntities.Params> action = ReactComponents.action(ActionPurgeEntities.ID);
    if (action == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }
    ActionPurgeEntities.Params params = action.getDefaultParams();

    if (!world.equals("ALL")) {
      params.withWorld(worldKey(world));
    }

    if (sender.isPlayer() && radius > 0) {
      WorldHost worlds = worldHost();
      Optional<GameLocation> location = worlds == null ? Optional.empty() : worlds.location(sender.player());
      Optional<ReactWorld> playerWorld = location.flatMap((GameLocation at) -> worlds.world(at.worldId()));
      if (location.isPresent() && playerWorld.isPresent()) {
        int chunkX = ((int) Math.floor(location.get().x())) >> 4;
        int chunkZ = ((int) Math.floor(location.get().z())) >> 4;
        params.addRadius(playerWorld.get(), chunkX, chunkZ, Math.min(radius, 10));
      }
    }

    action.create(params, sender).queue();
  }

  static void trimEntitiesByAgePriority(VolmitSender sender, int maxEntities, int minAgeSeconds, String world) {
    Action<ActionTrimEntitiesByAgePriority.Params> action = ReactComponents.action(ActionTrimEntitiesByAgePriority.ID);
    if (action == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }
    ActionTrimEntitiesByAgePriority.Params params = action.getDefaultParams()
        .setMaxTrim(Math.max(1, Math.min(maxEntities, 10_000)))
        .setMinEntityAgeTicks(Math.max(1, minAgeSeconds) * 20);

    if (!world.equals("ALL")) {
      params.withWorld(worldKey(world));
    }

    action.create(params, sender).queue();
  }

  private static WorldHost worldHost() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.worldHost().orElse(null);
  }
}
