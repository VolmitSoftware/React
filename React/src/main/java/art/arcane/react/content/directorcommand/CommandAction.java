/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
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
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.handlers.OptionalWorldHandler;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.react.util.project.config.ConfigLocalization;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

import java.util.Comparator;

@Director(
    name = "action",
    aliases = {"act", "a"},
    origin = DirectorOrigin.BOTH,
    description = "Action utilities for mitigation, cleanup, and operational diagnostics",
    descriptionKey = "command.description.action"
)
public class CommandAction implements DirectorExecutor {
  @Director(
      name = "purge-entities",
      aliases = {"pe"},
      description = "Remove matching entities in the selected world and chunk radius",
      descriptionKey = "command.description.action.purge_entities"
  )
  public void purgeEntities(
      @Param(
          name = "radius",
          description = "Chunk radius around you; zero uses all chunks",
          descriptionKey = "command.parameter.action.radius",
          defaultValue = "0",
          aliases = {"r"}
      )
      int radius,

      @Param(
          name = "world",
          description = "World targeted by this action",
          descriptionKey = "command.parameter.action.world",
          customHandler = OptionalWorldHandler.class,
          defaultValue = "ALL",
          aliases = {"w"}
      )
      String world
  ) {
    Action<ActionPurgeEntities.Params> pe = React.action("purge-entities");
    ActionPurgeEntities.Params p = pe.getDefaultParams();

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    if (sender().isPlayer()) {
      if (radius > 0) {
        Chunk c = player().getLocation().getChunk();
        p.addRadius(c.getWorld(), c.getX(), c.getZ(), Math.min(radius, 10));
      }
    }

    pe.create(p, sender()).queue();
  }

  @Director(
      name = "purge-chunks",
      aliases = {"pc"},
      description = "Attempt to unload chunks in the selected world",
      descriptionKey = "command.description.action.purge_chunks"
  )
  public void purgeChunks(
      @Param(
          name = "world",
          description = "World targeted by this action",
          descriptionKey = "command.parameter.action.world",
          customHandler = OptionalWorldHandler.class,
          defaultValue = "ALL",
          aliases = {"w"}
      )
      String world
  ) {
    Action<ActionPurgeChunks.Params> pc = React.action("purge-chunks");
    ActionPurgeChunks.Params p = pc.getDefaultParams();

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    pc.create(p, sender()).queue();
  }


  @Director(
      name = "collect-garbage",
      aliases = {"gc"},
      description = "Request JVM garbage collection and report immediate reclaimed heap",
      descriptionKey = "command.description.action.collect_garbage"
  )
  public void collectGarbage() {
    Action<ActionCollectGarbage.Params> pe = React.action("collect-garbage");
    ActionCollectGarbage.Params p = pe.getDefaultParams();
    pe.create(p, sender()).queue();
  }

  @Director(
      name = "quarantine-hot-chunks",
      aliases = {"aqhc"},
      description = "Temporarily isolate the hottest sampled chunks",
      descriptionKey = "command.description.action.quarantine_hot_chunks"
  )
  public void quarantineHotChunks(
      @Param(
          name = "max-chunks",
          description = "Maximum number of chunks targeted by this action",
          descriptionKey = "command.parameter.action.max_chunks",
          defaultValue = "24",
          aliases = {"m"}
      )
      int maxChunks,

      @Param(
          name = "min-score",
          description = "Minimum sampled chunk score to consider",
          descriptionKey = "command.parameter.action.min_score",
          defaultValue = "90",
          aliases = {"s"}
      )
      double minScore,

      @Param(
          name = "world",
          description = "World targeted by this action",
          descriptionKey = "command.parameter.action.world",
          customHandler = OptionalWorldHandler.class,
          defaultValue = "ALL",
          aliases = {"w"}
      )
      String world
  ) {
    Action<ActionQuarantineHotChunks.Params> action = React.action(ActionQuarantineHotChunks.ID);
    ActionQuarantineHotChunks.Params p = action.getDefaultParams()
        .setMaxChunks(Math.max(1, Math.min(maxChunks, 256)))
        .setMinimumChunkScore(Math.max(0D, minScore));

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    action.create(p, sender()).queue();
  }

  @Director(
      name = "trim-entities-by-age-priority",
      aliases = {"ateap"},
      description = "Trim old low-priority entities with safety guards",
      descriptionKey = "command.description.action.trim_entities"
  )
  public void trimEntitiesByAgePriority(
      @Param(
          name = "max-entities",
          description = "Maximum entities to trim in this action",
          descriptionKey = "command.parameter.action.max_entities",
          defaultValue = "600",
          aliases = {"m"}
      )
      int maxEntities,

      @Param(
          name = "min-age-seconds",
          description = "Minimum age in seconds before entities are eligible",
          descriptionKey = "command.parameter.action.min_age_seconds",
          defaultValue = "300",
          aliases = {"age"}
      )
      int minAgeSeconds,

      @Param(
          name = "world",
          description = "World targeted by this action",
          descriptionKey = "command.parameter.action.world",
          customHandler = OptionalWorldHandler.class,
          defaultValue = "ALL",
          aliases = {"w"}
      )
      String world
  ) {
    Action<ActionTrimEntitiesByAgePriority.Params> action = React.action(ActionTrimEntitiesByAgePriority.ID);
    ActionTrimEntitiesByAgePriority.Params p = action.getDefaultParams()
        .setMaxTrim(Math.max(1, Math.min(maxEntities, 10_000)))
        .setMinEntityAgeTicks(Math.max(1, minAgeSeconds) * 20);

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    action.create(p, sender()).queue();
  }

  @Director(
      name = "hopper-network-normalize",
      aliases = {"ahnn"},
      description = "Normalize hopper hotspots by merging nearby transfer items",
      descriptionKey = "command.description.action.hopper_network_normalize"
  )
  public void hopperNetworkNormalize(
      @Param(
          name = "max-chunks",
          description = "Maximum number of chunks targeted by this action",
          descriptionKey = "command.parameter.action.max_chunks",
          defaultValue = "20",
          aliases = {"m"}
      )
      int maxChunks,

      @Param(
          name = "min-hopper-updates",
          description = "Minimum hopper updates per chunk to be considered hot",
          descriptionKey = "command.parameter.action.min_hopper_updates",
          defaultValue = "25",
          aliases = {"u"}
      )
      double minHopperUpdates,

      @Param(
          name = "world",
          description = "World targeted by this action",
          descriptionKey = "command.parameter.action.world",
          customHandler = OptionalWorldHandler.class,
          defaultValue = "ALL",
          aliases = {"w"}
      )
      String world
  ) {
    Action<ActionHopperNetworkNormalize.Params> action = React.action(ActionHopperNetworkNormalize.ID);
    ActionHopperNetworkNormalize.Params p = action.getDefaultParams()
        .setMaxChunks(Math.max(1, Math.min(maxChunks, 256)))
        .setMinimumHopperUpdatesPerChunk(Math.max(1D, minHopperUpdates));

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    action.create(p, sender()).queue();
  }

  @Director(
      name = "prewarm-critical-chunks",
      aliases = {"apcc"},
      description = "Preload the most critical sampled chunks and neighbors",
      descriptionKey = "command.description.action.prewarm_critical_chunks"
  )
  public void prewarmCriticalChunks(
      @Param(
          name = "max-chunks",
          description = "Maximum number of chunks targeted by this action",
          descriptionKey = "command.parameter.action.max_chunks",
          defaultValue = "40",
          aliases = {"m"}
      )
      int maxChunks,

      @Param(
          name = "neighbor-radius",
          description = "Neighbor radius around each critical chunk",
          descriptionKey = "command.parameter.action.neighbor_radius",
          defaultValue = "1",
          aliases = {"r"}
      )
      int neighborRadius,

      @Param(
          name = "world",
          description = "World targeted by this action",
          descriptionKey = "command.parameter.action.world",
          customHandler = OptionalWorldHandler.class,
          defaultValue = "ALL",
          aliases = {"w"}
      )
      String world
  ) {
    Action<ActionPrewarmCriticalChunks.Params> action = React.action(ActionPrewarmCriticalChunks.ID);
    ActionPrewarmCriticalChunks.Params p = action.getDefaultParams()
        .setMaxChunks(Math.max(1, Math.min(maxChunks, 512)))
        .setNeighborRadius(Math.max(0, Math.min(neighborRadius, 4)));

    if (!world.equals("ALL")) {
      p.withWorld(WorldIdentity.resolve(world).orElseThrow());
    }

    action.create(p, sender()).queue();
  }

  @Director(
      name = "incident-playbook",
      aliases = {"aip"},
      description = "Queue a full lag-incident mitigation action sequence",
      descriptionKey = "command.description.action.incident_playbook"
  )
  public void incidentPlaybook(
      @Param(
          name = "include-gc",
          description = "Whether to include a garbage collection step",
          descriptionKey = "command.parameter.action.include_gc",
          defaultValue = "true",
          aliases = {"gc"}
      )
      boolean includeGC,

      @Param(
          name = "tier",
          description = "Force tier: -1 auto, 0 mild, 1 medium, 2 severe",
          descriptionKey = "command.parameter.action.tier",
          defaultValue = "-1",
          aliases = {"t"}
      )
      int tier,

      @Param(
          name = "world",
          description = "World targeted by this action",
          descriptionKey = "command.parameter.action.world",
          customHandler = OptionalWorldHandler.class,
          defaultValue = "ALL",
          aliases = {"w"}
      )
      String world
  ) {
    Action<ActionIncidentPlaybook.Params> action = React.action(ActionIncidentPlaybook.ID);
    ActionIncidentPlaybook.Params p = action.getDefaultParams()
        .setIncludeGarbageCollection(includeGC)
        .setTierOverride(Math.max(-1, Math.min(2, tier)));

    if (!world.equals("ALL")) {
      p.setWorld(WorldIdentity.parse(world).toString());
    }

    action.create(p, sender()).queue();
  }

  @Director(
      name = "audit",
      aliases = {"list", "ls"},
      description = "List all registered actions, enabled state, and current behavior summary",
      descriptionKey = "command.description.action.audit"
  )
  public void audit() {
    ActionController controller = React.controller(ActionController.class);
    if (controller == null || controller.getActions() == null) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }

    ReactLanguage.sendPrefixed(sender(), CommandMessages.ACTION_AUDIT_HEADER);
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
              sender(),
              CommandMessages.ACTION_AUDIT_ENTRY,
              MessageArgument.untrusted("id", action.getId()),
              MessageArgument.untrusted("state", state),
              MessageArgument.untrusted("summary", summary)
          );
        });
  }
}
