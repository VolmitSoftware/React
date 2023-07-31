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

package com.volmit.react.content.action;

import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;
import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.api.action.ActionParams;
import com.volmit.react.api.action.ActionTicket;
import com.volmit.react.api.action.ReactAction;
import com.volmit.react.core.controller.ActionController;
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
import java.util.Random;

public class ActionPurgeEntities extends ReactAction<ActionPurgeEntities.Params> {
    public static final String ID = "purge-entities";
    public static final String SHORT = "pe";
    private List<EntityType> defaultEntityList = new ArrayList<>(List.of(
            EntityType.ARMOR_STAND,
            EntityType.PLAYER,
            EntityType.ITEM_FRAME
    ));
    private boolean defaultBlacklist = true;
    private int secondsToPurge = 5;

    private transient int lowerBound = secondsToPurge - 1;
    private transient int upperBound = secondsToPurge + 2;
    private transient int randomDelay = new Random().nextInt(upperBound - lowerBound) + lowerBound;

    public ActionPurgeEntities() {
        super(ID);
    }

    @Edict.Command("/react action " + ID)
    @Edict.Aliases({"/react action " + SHORT, "/react a " + SHORT, "/react a " + ID})
    public static void command() {
        Action<ActionPurgeEntities.Params> pe = React.action(ID);
        ActionPurgeEntities.Params p = pe.getDefaultParams();
        pe.create(p, EdictContext.get().getSender()).queue();
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
        return "Purged " + ticket.getCount() + " Entities across " + ticket.getTotalWork() + " chunks in " + Form.duration(ticket.getDuration(), 1);
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        List<Chunk> c = pullChunks(ticket, React.controller(ActionController.class).getActionSpeedMultiplier());

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
                        .blacklist(defaultBlacklist)
                        .build())
                .build();
    }


    private void purge(Entity entity, ActionTicket<Params> ticket) {
        J.s(() -> React.kill(entity, randomDelay), (int) (20 * Math.random()));
        ticket.addCount();
    }

    private void purge(Chunk c, ActionTicket<Params> ticket) {
        for (Entity i : c.getEntities()) {
            if (ticket.getParams().entityFilter.allows(i.getType())) {
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
            new Spiraler(radius * 2, radius * 2, (xx, zz) -> area.getChunks().add(world.getChunkAt(x + xx, z + zz))).drain();
            return this;
        }
    }
}
