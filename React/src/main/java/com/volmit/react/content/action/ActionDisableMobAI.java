package com.volmit.react.content.action;

import com.volmit.react.React;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import com.volmit.react.model.AreaActionParams;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActionDisableMobAI extends ReactAction<ActionDisableMobAI.Params> {
    public static final String ID = "disable-mob-ai";

    public ActionDisableMobAI() {
        super(ID);
    }

    List<Chunk> pullChunks(ActionTicket<Params> ticket, int max) {
        List<Chunk> c = new ArrayList<>();

        for (int i = 0; i < max; i++) {
            Chunk cc = ticket.getParams().getArea().popChunk();

            if (cc == null) {
                break;
            }

            c.add(cc);
        }

        return c;
    }

    @Override
    public String getCompletedMessage(ActionTicket<Params> ticket) {
        return "Disabled AI for " + ticket.getCount() + " mobs";// for " + ticket.getParams().getDisableDuration() + " seconds";
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        List<Chunk> chunks = pullChunks(ticket, React.instance.getActionController().getActionSpeedMultiplier());

        if (ticket.getTotalWork() <= 0) {
            ticket.setTotalWork(ticket.getParams().getArea().getChunks().size());
        }

        if (chunks.isEmpty()) {
            ticket.complete();
        } else {
            chunks.forEach(chunk -> disableAI(chunk, ticket));
            ticket.addWork(chunks.size());
        }
    }

    @Override
    public Params getDefaultParams() {
        return Params.builder()
                .build();
    }

    private void disableAI(Chunk chunk, ActionTicket<Params> ticket) {
        List<Entity> entities = Arrays.stream(chunk.getEntities())
                .filter(entity -> entity instanceof Mob)
                .toList();

        entities.forEach(entity -> {
            LivingEntity livingEntity = (LivingEntity) entity;
            livingEntity.setAI(false);
            livingEntity.setCollidable(false);
            livingEntity.setCanPickupItems(false);
            livingEntity.setRotation(0, 0);
//            J.s(() -> {
//                livingEntity.setAI(true);
//                livingEntity.setCollidable(true);
//                livingEntity.setCanPickupItems(true);
//                livingEntity.setInvisible(false);
//            }, 20 * ticket.getParams().getDisableDuration());  // Convert seconds to ticks
            ticket.addCount();
        });
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
        private int disableDuration = 10;

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
