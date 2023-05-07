package com.volmit.react.content.action;

import com.volmit.react.React;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import com.volmit.react.api.arguments.Argument;
import com.volmit.react.legacyutil.J;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ActionPurgeEntities extends ReactAction<ActionPurgeEntities.Params> {
    public static final String ID = "purge-entities";

    public ActionPurgeEntities() {
        super(ID);
    }

    List<Chunk> pullChunks(ActionTicket<Params> ticket, int max) {
        if(ticket.getParams().getQueue() == null || ticket.getParams().getQueue().isEmpty()) {
           List<Chunk> c = new ArrayList<>();

           if(ticket.getParams().getWorld() != null) {
               c.addAll(Arrays.asList(J.sResult(() -> Bukkit.getWorld(ticket.getParams().getWorld()).getLoadedChunks())));
           }

           else {
               for(World i : Bukkit.getWorlds()) {
                   c.addAll(Arrays.asList(J.sResult(i::getLoadedChunks)));
               }
           }

           ticket.getParams().setQueue(c);
        }

        List<Chunk> c = new ArrayList<>();

        for(int i = 0; i < max; i++) {
            if(ticket.getParams().getQueue().isEmpty()) {
                break;
            }

            c.add(ticket.getParams().getQueue().remove(0));
        }

        return c;
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        List<Chunk> c = pullChunks(ticket, React.instance.getActionController().getActionSpeedMultiplier());

        if (ticket.getTotalWork() <= 1) {
            ticket.setTotalWork(ticket.getParams().getQueue().size());
        }

        if (c.isEmpty()) {
            ticket.complete();
        } else {
            for (Chunk i : c) {
                purge(i, ticket.getParams());
            }

            ticket.addWork(c.size());
        }
    }

    @Override
    public Params getDefaultParams() {
        return Params.builder().build();
    }

    private void purge(Entity entity, Params purgeEntitiesParams) {
        J.s(entity::remove, 20 + (int)(100 * Math.random()));
        J.s(() -> {
            entity.setFreezeTicks(entity.getMaxFreezeTicks());
            entity.setGravity(false);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);

            if(entity instanceof LivingEntity le) {
                le.setAI(false);
                le.setCollidable(false);
                le.setInvisible(true);
            }
        });
    }

    private void purge(Chunk c, Params purgeEntitiesParams) {
        for(Entity i : c.getEntities()) {
            if(purgeEntitiesParams.canPurge(i.getType())) {
                purge(i, purgeEntitiesParams);
            }
        }
    }

    @Override
    public void onInit() {

    }

    @Builder
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Params implements ActionParams {
        private transient List<Chunk> queue;

        @Argument(
            name = "world",
            shortCode = "w",
            description = "World to purge entities in"
        )
        String world;

        @Argument(
            name = "only",
            shortCode = "o",
            description = "Purge only the following entity types",
            listType = EntityType.class
        )
        @Builder.Default
        Set<EntityType> only = new HashSet<>();

        @Argument(
            name = "ignore",
            shortCode = "i",
            description = "Purge all entity types ignoring the following",
            listType = EntityType.class
        )

        @Builder.Default
        Set<EntityType> except = new HashSet<>();

        public boolean canPurge(EntityType entity) {
            if(only != null && !only.isEmpty()) {
                return only.contains(entity);
            }

            if(except != null && !except.isEmpty()) {
                return !except.contains(entity);
            }

            return true;
        }
    }
}
