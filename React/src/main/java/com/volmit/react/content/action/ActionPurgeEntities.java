package com.volmit.react.content.action;

import com.volmit.react.React;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import com.volmit.react.model.AreaActionParams;
import com.volmit.react.model.FilterParams;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.math.Spiraler;
import com.volmit.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
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
        List<Chunk> c = new ArrayList<>();

        for(int i = 0; i < max; i++) {
            Chunk cc = ticket.getParams().getArea().popChunk();

            if(cc == null) {
                break;
            }
        }

        return c;
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
            if(purgeEntitiesParams.entityFilter.allows(i.getType())) {
                purge(i, purgeEntitiesParams);
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
        private AreaActionParams area = new AreaActionParams();
        private FilterParams<EntityType> entityFilter = FilterParams.<EntityType>builder()
            .blacklist(true)
            .type(EntityType.ARMOR_STAND)
            .type(EntityType.PLAYER)
            .type(EntityType.ITEM_FRAME)
            .type(EntityType.DROPPED_ITEM)
            .type(EntityType.EXPERIENCE_ORB)
            .build();

        public Params withWorld(World world) {
            area.setWorld(world.getName());
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
