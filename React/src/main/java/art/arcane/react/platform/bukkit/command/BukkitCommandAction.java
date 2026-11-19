package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.content.action.ActionCaptureProfile;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionHopperNetworkNormalize;
import art.arcane.react.content.action.ActionIncidentPlaybook;
import art.arcane.react.content.action.ActionPrewarmCriticalChunks;
import art.arcane.react.content.action.ActionPurgeChunks;
import art.arcane.react.content.action.ActionPurgeEntities;
import art.arcane.react.content.action.ActionQuarantineHotChunks;
import art.arcane.react.content.action.ActionTrimEntitiesByAgePriority;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.director.handlers.OptionalWorldHandler;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.react.util.project.config.ConfigLocalization;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

import java.util.Comparator;

public final class BukkitCommandAction {
  public void captureProfile(VolmitSender sender, int seconds) {
    Action<ActionCaptureProfile.Params> action = React.action(ActionCaptureProfile.ID);
    action.create(action.getDefaultParams().setSeconds(seconds), BukkitCommandSender.bukkit(sender)).queue();
  }

  public void purgeEntities(VolmitSender sender, int radius, String world) {
    Action<ActionPurgeEntities.Params> pe = React.action("purge-entities");
    ActionPurgeEntities.Params p = pe.getDefaultParams();

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    if (sender.isPlayer()) {
      if (radius > 0) {
        Chunk c = BukkitCommandSender.player(sender).getLocation().getChunk();
        p.addRadius(c.getWorld(), c.getX(), c.getZ(), Math.min(radius, 10));
      }
    }

    pe.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

  public void purgeChunks(VolmitSender sender, String world) {
    Action<ActionPurgeChunks.Params> pc = React.action("purge-chunks");
    ActionPurgeChunks.Params p = pc.getDefaultParams();

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    pc.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

  public void collectGarbage(VolmitSender sender) {
    Action<ActionCollectGarbage.Params> pe = React.action("collect-garbage");
    ActionCollectGarbage.Params p = pe.getDefaultParams();
    pe.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

  public void quarantineHotChunks(VolmitSender sender, int maxChunks, double minScore, String world) {
    Action<ActionQuarantineHotChunks.Params> action = React.action(ActionQuarantineHotChunks.ID);
    ActionQuarantineHotChunks.Params p = action.getDefaultParams()
        .setMaxChunks(Math.max(1, Math.min(maxChunks, 256)))
        .setMinimumChunkScore(Math.max(0D, minScore));

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    action.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

  public void trimEntitiesByAgePriority(VolmitSender sender, int maxEntities, int minAgeSeconds, String world) {
    Action<ActionTrimEntitiesByAgePriority.Params> action = React.action(ActionTrimEntitiesByAgePriority.ID);
    ActionTrimEntitiesByAgePriority.Params p = action.getDefaultParams()
        .setMaxTrim(Math.max(1, Math.min(maxEntities, 10_000)))
        .setMinEntityAgeTicks(Math.max(1, minAgeSeconds) * 20);

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    action.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

  public void hopperNetworkNormalize(VolmitSender sender, int maxChunks, double minHopperUpdates, String world) {
    Action<ActionHopperNetworkNormalize.Params> action = React.action(ActionHopperNetworkNormalize.ID);
    ActionHopperNetworkNormalize.Params p = action.getDefaultParams()
        .setMaxChunks(Math.max(1, Math.min(maxChunks, 256)))
        .setMinimumHopperUpdatesPerChunk(Math.max(1D, minHopperUpdates));

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    action.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

  public void prewarmCriticalChunks(VolmitSender sender, int maxChunks, int neighborRadius, String world) {
    Action<ActionPrewarmCriticalChunks.Params> action = React.action(ActionPrewarmCriticalChunks.ID);
    ActionPrewarmCriticalChunks.Params p = action.getDefaultParams()
        .setMaxChunks(Math.max(1, Math.min(maxChunks, 512)))
        .setNeighborRadius(Math.max(0, Math.min(neighborRadius, 4)));

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    action.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

  public void incidentPlaybook(VolmitSender sender, boolean includeGC, int tier, String world) {
    Action<ActionIncidentPlaybook.Params> action = React.action(ActionIncidentPlaybook.ID);
    ActionIncidentPlaybook.Params p = action.getDefaultParams()
        .setIncludeGarbageCollection(includeGC)
        .setTierOverride(Math.max(-1, Math.min(2, tier)));

    if (!world.equals("ALL")) {
      p.setWorld(WorldIdentity.parse(world).toString());
    }

    action.create(p, BukkitCommandSender.bukkit(sender)).queue();
  }

  public void audit(VolmitSender sender) {
    ActionController controller = React.controller(ActionController.class);
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
}
