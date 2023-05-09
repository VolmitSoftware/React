package com.volmit.react.content.action;

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

import java.util.ArrayList;
import java.util.List;


public class ActionRedstoneResume extends ReactAction<ActionRedstoneResume.Params> {
    public static final String ID = "redstone-resume";

    public ActionRedstoneResume() {
        super(ID);
    }

    @Override
    public String getCompletedMessage(ActionTicket<Params> ticket) {
        return "Resumed redstone in " + ticket.getCount() + " chunks after " + Form.duration(ticket.getDuration(), 1);
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
                resume(i, ticket);
            }

            ticket.addWork(c.size());
        }
    }

    private void resume(Chunk c, ActionTicket<Params> ticket) {
        ActionRedstoneHalt.haltedChunks.remove(c);
        ticket.addCount();
    }

    @Override
    public Params getDefaultParams() {
        return Params.builder().build();
    }

    @Override
    public void onInit() {

    }

    @Builder
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Params implements ActionParams {
        @Builder.Default
        private AreaActionParams area = AreaActionParams.builder().build();
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


