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

import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.handlers.OptionalWorldHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(
    name = "action",
    aliases = {"act", "a"},
    origin = DirectorOrigin.BOTH,
    description = "Action utilities for mitigation, cleanup, and operational diagnostics",
    descriptionKey = "command.description.action"
)
public class CommandAction implements DirectorExecutor {
  @Director(
      name = "capture-profile",
      description = "Save a local JDK Flight Recorder profile without uploading it",
      descriptionKey = "command.description.action.capture_profile"
  )
  public void captureProfile(
      @Param(
          name = "seconds",
          description = "Capture duration in seconds, from 1 to 300",
          descriptionKey = "command.parameter.action.profile_seconds",
          defaultValue = "30"
      )
      int seconds
  ) {
    ActionCommands.captureProfile(sender(), seconds);
  }

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
    ActionCommands.purgeEntities(sender(), radius, world);
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
    ActionCommands.purgeChunks(sender(), world);
  }

  @Director(
      name = "collect-garbage",
      aliases = {"gc"},
      description = "Request JVM garbage collection and report immediate reclaimed heap",
      descriptionKey = "command.description.action.collect_garbage"
  )
  public void collectGarbage() {
    ActionCommands.collectGarbage(sender());
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
    ActionCommands.quarantineHotChunks(sender(), maxChunks, minScore, world);
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
    ActionCommands.trimEntitiesByAgePriority(sender(), maxEntities, minAgeSeconds, world);
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
    ActionCommands.hopperNetworkNormalize(sender(), maxChunks, minHopperUpdates, world);
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
    ActionCommands.prewarmCriticalChunks(sender(), maxChunks, neighborRadius, world);
  }

  @Director(
      name = "incident-playbook",
      aliases = {"aip"},
      description = "Run relevant incident mitigations one at a time while pressure remains",
      descriptionKey = "command.description.action.incident_playbook"
  )
  public void incidentPlaybook(
      @Param(
          name = "include-gc",
          description = "Allow garbage collection when heap pressure and reclaimable garbage are high",
          descriptionKey = "command.parameter.action.include_gc",
          defaultValue = "false",
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
    ActionCommands.incidentPlaybook(sender(), includeGC, tier, world);
  }

  @Director(
      name = "audit",
      aliases = {"list", "ls"},
      description = "List all registered actions, enabled state, and current behavior summary",
      descriptionKey = "command.description.action.audit"
  )
  public void audit() {
    ActionCommands.audit(sender());
  }
}
