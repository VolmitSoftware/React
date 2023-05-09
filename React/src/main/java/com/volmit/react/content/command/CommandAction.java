package com.volmit.react.content.command;

import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.content.action.*;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.decree.annotations.Param;
import com.volmit.react.util.decree.handlers.OptionalWorldHandler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

@Decree(
        name = "action",
        aliases = {"act", "a"},
        origin = DecreeOrigin.BOTH,
        description = "The action command"
)
public class CommandAction implements DecreeExecutor {
    @Decree(
            name = "purge-entities",
            aliases = {"pe", "kill"},
            description = "Purge entities in the area"
    )
    public void purgeEntities(
            @Param(
                    name = "radius",
                    description = "The chunk radius around you to purge entities from. 0 is all chunks.",
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
        Action<ActionPurgeEntities.Params> pe = React.instance.getActionController().getAction("purge-entities");
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
            name = "purge-dropped-items",
            aliases = {"pd", "drops"},
            description = "Purge dropped items in the area"
    )
    public void purgeDroppedItems(
            @Param(
                    name = "radius",
                    description = "The chunk radius around you to purge dropped items from. 0 is all chunks.",
                    defaultValue = "0",
                    aliases = {"r"}
            )
            int radius,

            @Param(
                    name = "world",
                    description = "The world to purge dropped items from.",
                    customHandler = OptionalWorldHandler.class,
                    defaultValue = "ALL",
                    aliases = {"w"}
            )
            String world
    ) {
        Action<ActionPurgeDroppedItems.Params> pe = React.instance.getActionController().getAction("purge-dropped-items");
        ActionPurgeDroppedItems.Params p = pe.getDefaultParams();

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
            aliases = {"pc", "chunks"},
            description = "UNloads the chunks in the world"
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
        Action<ActionPurgeChunks.Params> pc = React.instance.getActionController().getAction("purge-chunks");
        ActionPurgeChunks.Params p = pc.getDefaultParams();

        if (!world.equals("ALL")) {
            p.withWorld(Bukkit.getWorld(world));
        }

        pc.create(p, sender()).queue();
    }

    @Decree(
            name = "redstone-halt",
            aliases = {"rh", "halt"},
            description = "Halt redstone activity in the area"
    )
    public void haltRedstone(
            @Param(
                    name = "radius",
                    description = "The chunk radius around you to halt redstone activity. 0 is all chunks.",
                    defaultValue = "0",
                    aliases = {"r"}
            )
            int radius,

            @Param(
                    name = "world",
                    description = "The world to halt redstone activity.",
                    customHandler = OptionalWorldHandler.class,
                    defaultValue = "ALL",
                    aliases = {"w"}
            )
            String world,

            @Param(
                    name = "seconds",
                    description = "Duration to halt redstone activity. 0 is forever.",
                    defaultValue = "0",
                    aliases = {"s", "sec"}
            )
            int seconds
    ) {
        Action<ActionRedstoneHalt.Params> rh = React.instance.getActionController().getAction("redstone-halt");
        ActionRedstoneHalt.Params p = rh.getDefaultParams();

        if (!world.equals("ALL")) {
            p.withWorld(Bukkit.getWorld(world));
        }

        if (sender().isPlayer()) {
            if (radius > 0) {
                Chunk c = player().getLocation().getChunk();
                p.addRadius(c.getWorld(), c.getX(), c.getZ(), Math.min(radius, 10));
            }
        }

        p.setDuration(seconds);
        rh.create(p, sender()).queue();
    }

    @Decree(
            name = "redstone-resume",
            aliases = {"rr", "resume"},
            description = "Resume redstone activity in the area"
    )
    public void resumeRedstone(
            @Param(
                    name = "radius",
                    description = "The chunk radius around you to resume redstone activity. 0 is all chunks.",
                    defaultValue = "0",
                    aliases = {"r"}
            )
            int radius,

            @Param(
                    name = "world",
                    description = "The world to resume redstone activity.",
                    customHandler = OptionalWorldHandler.class,
                    defaultValue = "ALL",
                    aliases = {"w"}
            )
            String world
    ) {
        Action<ActionRedstoneResume.Params> rr = React.instance.getActionController().getAction("redstone-resume");
        ActionRedstoneResume.Params p = rr.getDefaultParams();

        if (!world.equals("ALL")) {
            p.withWorld(Bukkit.getWorld(world));
        }

        if (sender().isPlayer()) {
            if (radius > 0) {
                Chunk c = player().getLocation().getChunk();
                p.addRadius(c.getWorld(), c.getX(), c.getZ(), Math.min(radius, 10));
            }
        }

        rr.create(p, sender()).queue();
    }

    @Decree(
            name = "collect-garbage",
            aliases = {"gc"},
            description = "Run system gc"
    )
    public void collectGarbage() {
        Action<ActionCollectGarbage.Params> pe = React.instance.getActionController().getAction("collect-garbage");
        ActionCollectGarbage.Params p = pe.getDefaultParams();
        pe.create(p, sender()).queue();
    }
}
