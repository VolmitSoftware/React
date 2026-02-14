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

package art.arcane.react.content.decreecommand;

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
import art.arcane.react.util.decree.DecreeExecutor;
import art.arcane.volmlib.util.decree.DecreeOrigin;
import art.arcane.volmlib.util.decree.annotations.Decree;
import art.arcane.volmlib.util.decree.annotations.Param;
import art.arcane.react.util.decree.handlers.OptionalWorldHandler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

@Decree(
        name = "action",
        aliases = {"act", "a"},
        origin = DecreeOrigin.BOTH,
        description = "This is the root action command, it contains all current actions"
)
public class CommandAction implements DecreeExecutor {
    @Decree(
            name = "purge-entities",
            aliases = {"pe"},
            description = "this Kills/Deletes all entities in the specified region"
    )
    public void purgeEntities(
            @Param(
                    name = "radius",
                    description = "The chunk radius around you to purge entities from. 0/Blank is all chunks.",
                    defaultValue = "0",
                    aliases = {"r"}
            )
            int radius,

            @Param(
                    name = "world",
                    description = "The world to purge entities from.",
                    customHandler = OptionalWorldHandler.class,
                    defaultValue = "ALL",
                    aliases = {"w"}
            )
            String world
    ) {
        Action<ActionPurgeEntities.Params> pe = React.action("purge-entities");
        ActionPurgeEntities.Params p = pe.getDefaultParams();

        if (!world.equals("ALL")) {
            p.withWorld(Bukkit.getWorld(world));
        }

        if (sender().isPlayer()) {
            if (radius > 0) {
                Chunk c = player().getLocation().getChunk();
                p.addRadius(c.getWorld(), c.getX(), c.getZ(), Math.min(radius, 10));
            }
        }

        pe.create(p, sender()).queue();
    }

    @Decree(
            name = "purge-chunks",
            aliases = {"pc"},
            description = "this unloads chunks in the specified region, if applicable in the specified world"
    )
    public void purgeChunks(
            @Param(
                    name = "world",
                    description = "The world to unload chunks from.",
                    customHandler = OptionalWorldHandler.class,
                    defaultValue = "ALL",
                    aliases = {"w"}
            )
            String world
    ) {
        Action<ActionPurgeChunks.Params> pc = React.action("purge-chunks");
        ActionPurgeChunks.Params p = pc.getDefaultParams();

        if (!world.equals("ALL")) {
            p.withWorld(Bukkit.getWorld(world));
        }

        pc.create(p, sender()).queue();
    }


    @Decree(
            name = "collect-garbage",
            aliases = {"gc"},
            description = "Run a system gc, unnecessary on most systems, but may help on some. use with caution."
    )
    public void collectGarbage() {
        Action<ActionCollectGarbage.Params> pe = React.action("collect-garbage");
        ActionCollectGarbage.Params p = pe.getDefaultParams();
        pe.create(p, sender()).queue();
    }

    @Decree(
            name = "quarantine-hot-chunks",
            aliases = {"aqhc"},
            description = "Temporarily isolate the hottest sampled chunks"
    )
    public void quarantineHotChunks(
            @Param(
                    name = "max-chunks",
                    description = "Maximum amount of hot chunks to quarantine",
                    defaultValue = "24",
                    aliases = {"m"}
            )
            int maxChunks,

            @Param(
                    name = "min-score",
                    description = "Minimum sampled chunk score to consider",
                    defaultValue = "90",
                    aliases = {"s"}
            )
            double minScore,

            @Param(
                    name = "world",
                    description = "The world to quarantine hot chunks in.",
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
            p.withWorld(Bukkit.getWorld(world));
        }

        action.create(p, sender()).queue();
    }

    @Decree(
            name = "trim-entities-by-age-priority",
            aliases = {"ateap"},
            description = "Trim old low-priority entities with safety guards"
    )
    public void trimEntitiesByAgePriority(
            @Param(
                    name = "max-entities",
                    description = "Maximum entities to trim in this action",
                    defaultValue = "600",
                    aliases = {"m"}
            )
            int maxEntities,

            @Param(
                    name = "min-age-seconds",
                    description = "Minimum age in seconds before entities are eligible",
                    defaultValue = "300",
                    aliases = {"age"}
            )
            int minAgeSeconds,

            @Param(
                    name = "world",
                    description = "The world to trim entities in.",
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
            p.withWorld(Bukkit.getWorld(world));
        }

        action.create(p, sender()).queue();
    }

    @Decree(
            name = "hopper-network-normalize",
            aliases = {"ahnn"},
            description = "Normalize hopper hotspots by merging nearby transfer items"
    )
    public void hopperNetworkNormalize(
            @Param(
                    name = "max-chunks",
                    description = "Maximum hotspot chunks to normalize",
                    defaultValue = "20",
                    aliases = {"m"}
            )
            int maxChunks,

            @Param(
                    name = "min-hopper-updates",
                    description = "Minimum hopper updates per chunk to be considered hot",
                    defaultValue = "25",
                    aliases = {"u"}
            )
            double minHopperUpdates,

            @Param(
                    name = "world",
                    description = "The world to normalize hopper networks in.",
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
            p.withWorld(Bukkit.getWorld(world));
        }

        action.create(p, sender()).queue();
    }

    @Decree(
            name = "prewarm-critical-chunks",
            aliases = {"apcc"},
            description = "Preload the most critical sampled chunks and neighbors"
    )
    public void prewarmCriticalChunks(
            @Param(
                    name = "max-chunks",
                    description = "Maximum chunks to prewarm",
                    defaultValue = "40",
                    aliases = {"m"}
            )
            int maxChunks,

            @Param(
                    name = "neighbor-radius",
                    description = "Neighbor radius around each critical chunk",
                    defaultValue = "1",
                    aliases = {"r"}
            )
            int neighborRadius,

            @Param(
                    name = "world",
                    description = "The world to prewarm chunks in.",
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
            p.withWorld(Bukkit.getWorld(world));
        }

        action.create(p, sender()).queue();
    }

    @Decree(
            name = "incident-playbook",
            aliases = {"aip"},
            description = "Queue a full lag-incident mitigation action sequence"
    )
    public void incidentPlaybook(
            @Param(
                    name = "include-gc",
                    description = "Whether to include a garbage collection step",
                    defaultValue = "true",
                    aliases = {"gc"}
            )
            boolean includeGC,

            @Param(
                    name = "tier",
                    description = "Force tier: -1 auto, 0 mild, 1 medium, 2 severe",
                    defaultValue = "-1",
                    aliases = {"t"}
            )
            int tier,

            @Param(
                    name = "world",
                    description = "Optional world filter for sub-actions.",
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
            if (Bukkit.getWorld(world) != null) {
                p.setWorld(Bukkit.getWorld(world).getName());
            }
        }

        action.create(p, sender()).queue();
    }
}
