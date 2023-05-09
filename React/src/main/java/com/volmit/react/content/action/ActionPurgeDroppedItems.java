package com.volmit.react.content.action;

import com.volmit.react.React;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import com.volmit.react.model.AreaActionParams;
import com.volmit.react.model.FilterParams;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.Spiraler;
import com.volmit.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class ActionPurgeDroppedItems extends ReactAction<ActionPurgeDroppedItems.Params> {
    public static final String ID = "purge-dropped-items";
    private List<EntityType> defaultEntityList = new ArrayList<>(List.of(EntityType.DROPPED_ITEM));

    public ActionPurgeDroppedItems() {
        super(ID);
    }

    List<Chunk> pullChunks(ActionTicket<Params> ticket, int max) {
        List<Chunk> c = new ArrayList<>();

        for(int i = 0; i < max; i++) {
            Chunk cc = ticket.getParams().getArea().popChunk();

            if(cc == null) {
                break;
            }

            c.add(cc);
        }

        return c;
    }

    @Override
    public String getCompletedMessage(ActionTicket<Params> ticket) {
        return "Purged " + ticket.getCount() + " dropped items across " + ticket.getTotalWork() + " chunks in " + Form.duration(ticket.getDuration(), 1);
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        List<Chunk> c = pullChunks(ticket, React.instance.getActionController().getActionSpeedMultiplier());

        if (ticket.getTotalWork() <= 1) {
            ticket.setTotalWork(ticket.getParams().getArea().getChunks().size());
        }

        if (c.isEmpty()) {
            ticket.complete();
        } else {
            for (Chunk i : c) {
                purge(i, ticket);
            }

            ticket.addWork(c.size());
        }
    }

    @Override
    public Params getDefaultParams() {
        return Params.builder()
                .entityFilter(FilterParams.<EntityType>builder()
                        .types(defaultEntityList)
                        .build())
                .build();
    }

    private void purge(Entity entity, ActionTicket<Params> ticket) {
        J.s(entity::remove, (int)(20 * Math.random()));
        ticket.addCount();
    }

    private void purge(Chunk c,  ActionTicket<Params> ticket) {
        for(Entity i : c.getEntities()) {
            if(ticket.getParams().entityFilter.allows(i.getType())) {
                purge(i, ticket);
            }
        }
    }

    @Override
    public void onInit() {

    }

    @Builder
    @Data
    @Accessors(
            chain = true
    )
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Params implements ActionParams {
        @Builder.Default
        private AreaActionParams area = AreaActionParams.builder().build();
        @Builder.Default
        private FilterParams<EntityType> entityFilter = FilterParams.<EntityType>builder().build();

        public Params withWorld(World world) {
            area.setWorld(world.getName());
            area.setAllChunks(true);
            return this;
        }

        public Params addRadius(World world, int x, int z, int radius) {
            area.setChunks(area.getChunks() == null ? new ArrayList<>() : new ArrayList<>(area.getChunks()));
            new Spiraler(radius * 2, radius * 2, (xx, zz) -> {
                area.getChunks().add(world.getChunkAt(x + xx, z + zz));
            }).drain();
            return this;
        }
    }
}
