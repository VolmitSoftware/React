package com.volmit.react.content.action;

import com.volmit.react.React;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import com.volmit.react.model.AreaActionParams;
import com.volmit.react.model.FilterParams;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.Spiraler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.*;

public class ActionRedstoneHalt extends ReactAction<ActionRedstoneHalt.Params> implements Listener {
    public static final String ID = "redstone-halt";
    public static Set<Chunk> haltedChunks = Collections.synchronizedSet(new HashSet<>());

    public ActionRedstoneHalt() {
        super(ID);
        React.instance.getServer().getPluginManager().registerEvents(this, React.instance);
    }

    @Override
    public String getCompletedMessage(ActionTicket<Params> ticket) {
        return "Halted redstone in " + ticket.getCount() + " chunks for " + Form.duration(ticket.getDuration(), 1);
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        List<Chunk> c = ticket.getParams().getArea().getChunks();

        if (ticket.getTotalWork() <= 1) {
            ticket.setTotalWork(c.size());
        }

        if (c.isEmpty()) {
            ticket.complete();
        } else {
            for (Chunk i : c) {
                halt(i, ticket);
            }

            ticket.addWork(c.size());
        }

        if (ticket.getParams().getDuration() > 0) {
            React.instance.getServer().getScheduler().runTaskLater(React.instance, ticket::complete, ticket.getParams().getDuration() * 20L);
        }
    }

    private void halt(Chunk c, ActionTicket<Params> ticket) {
        haltedChunks.add(c);
        ticket.addCount();
    }

    @Override
    public Params getDefaultParams() {
        return Params.builder().build();
    }

    @Override
    public void onInit() {

    }

    @EventHandler
    public void onRedstoneEvent(BlockRedstoneEvent event) {
        if (haltedChunks.contains(event.getBlock().getChunk())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @Builder
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Params implements ActionParams {
        @Builder.Default
        private AreaActionParams area = AreaActionParams.builder().build();
        private int duration;
        private int radius = 0;
        private Location center = null;

        public Params withWorld(World world) {
            area.setWorld(world.getName());
            area.setAllChunks(radius <= 0);
            return this;
        }

        public Params addRadius(World world, int x, int z, int radius) {
            this.radius = radius;
            this.center = new Location(world, x * 16, 0, z * 16);
            area.setAllChunks(false);
            return this;
        }
    }
}