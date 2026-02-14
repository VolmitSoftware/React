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

package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.model.AreaActionParams;
import art.arcane.react.model.FilterParams;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.Spiraler;
import art.arcane.react.util.scheduling.J;
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

@art.arcane.react.util.config.ConfigDescription("Configuration for Purge Entities action. This action performs targeted remediation when React decides intervention is needed.")
public class ActionPurgeEntities extends ReactAction<ActionPurgeEntities.Params> {
    public static final String ID = "purge-entities";
    public static final String SHORT = "pe";
    private List<EntityType> blacklist = new ArrayList<>(List.of(
            EntityType.ITEM_DISPLAY, EntityType.PLAYER, EntityType.ARMOR_STAND, EntityType.ITEM_FRAME, EntityType.PAINTING, EntityType.LEASH_HITCH,
            EntityType.MINECART, EntityType.MINECART_CHEST, EntityType.MINECART_COMMAND, EntityType.MINECART_FURNACE,
            EntityType.MINECART_HOPPER, EntityType.MINECART_MOB_SPAWNER, EntityType.MINECART_TNT, EntityType.BOAT,
            EntityType.FALLING_BLOCK, EntityType.DROPPED_ITEM, EntityType.EXPERIENCE_ORB, EntityType.FISHING_HOOK,
            EntityType.PRIMED_TNT, EntityType.SPLASH_POTION, EntityType.THROWN_EXP_BOTTLE, EntityType.ENDER_PEARL,
            EntityType.ENDER_SIGNAL, EntityType.FIREWORK, EntityType.LIGHTNING, EntityType.SHULKER_BULLET,
            EntityType.SMALL_FIREBALL, EntityType.SNOWBALL, EntityType.SPECTRAL_ARROW, EntityType.SPLASH_POTION,
            EntityType.THROWN_EXP_BOTTLE
    ));
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether purge entities applies default blacklist.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean defaultBlacklist = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Minimum age in seconds before purge entities removes matching entities.", impact = "Lower values purge newer entities sooner; higher values allow entities to remain longer.")
    private int secondsToPurge = 5;

    private transient int lowerBound = secondsToPurge - 1;
    private transient int upperBound = secondsToPurge + 2;
    private transient int randomDelay = new Random().nextInt(upperBound - lowerBound) + lowerBound;

    public ActionPurgeEntities() {
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
                        .types(blacklist)
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
        @art.arcane.react.util.config.ConfigDoc(value = "Area selection used by purge entities when choosing target chunks or entities.", impact = "Choose a tighter area for safer, local actions or a wider area for broader remediation.")
        private AreaActionParams area = AreaActionParams.builder().build();
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Filter definition for entity filter used by purge entities.", impact = "Narrow this list to target fewer cases, or broaden it to include more matching entries.")
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
