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
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ActionMessages;
import art.arcane.react.model.AreaActionParams;
import art.arcane.react.model.FilterParams;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.entity.StackExclusion;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.math.Spiraler;
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
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Purge Entities action. Removes matching entities from selected chunks or worlds.")
public class ActionPurgeEntities extends ReactAction<ActionPurgeEntities.Params> {
  public static final String ID = "purge-entities";
  private List<EntityType> blacklist = new ArrayList<>(List.of(
      EntityType.ITEM_DISPLAY, EntityType.PLAYER, EntityType.ARMOR_STAND, EntityType.ITEM_FRAME, EntityType.PAINTING, EntityType.LEASH_KNOT,
      EntityType.MINECART, EntityType.CHEST_MINECART, EntityType.COMMAND_BLOCK_MINECART, EntityType.FURNACE_MINECART,
      EntityType.HOPPER_MINECART, EntityType.SPAWNER_MINECART, EntityType.TNT_MINECART,
      EntityType.ACACIA_BOAT, EntityType.BAMBOO_RAFT, EntityType.BIRCH_BOAT, EntityType.CHERRY_BOAT, EntityType.DARK_OAK_BOAT,
      EntityType.JUNGLE_BOAT, EntityType.MANGROVE_BOAT, EntityType.OAK_BOAT, EntityType.PALE_OAK_BOAT, EntityType.SPRUCE_BOAT,
      EntityType.ACACIA_CHEST_BOAT, EntityType.BAMBOO_CHEST_RAFT, EntityType.BIRCH_CHEST_BOAT, EntityType.CHERRY_CHEST_BOAT,
      EntityType.DARK_OAK_CHEST_BOAT, EntityType.JUNGLE_CHEST_BOAT, EntityType.MANGROVE_CHEST_BOAT, EntityType.OAK_CHEST_BOAT,
      EntityType.PALE_OAK_CHEST_BOAT, EntityType.SPRUCE_CHEST_BOAT,
      EntityType.FALLING_BLOCK, EntityType.ITEM, EntityType.EXPERIENCE_ORB, EntityType.FISHING_BOBBER,
      EntityType.TNT, EntityType.SPLASH_POTION, EntityType.EXPERIENCE_BOTTLE, EntityType.ENDER_PEARL,
      EntityType.EYE_OF_ENDER, EntityType.FIREWORK_ROCKET, EntityType.LIGHTNING_BOLT, EntityType.SHULKER_BULLET,
      EntityType.SMALL_FIREBALL, EntityType.SNOWBALL, EntityType.SPECTRAL_ARROW, EntityType.SPLASH_POTION,
      EntityType.EXPERIENCE_BOTTLE
  ));
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether purge entities applies default blacklist.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean defaultBlacklist = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum age in seconds before purge entities removes matching entities.", impact = "Lower values purge newer entities sooner; higher values allow entities to remain longer.")
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
    return ReactLanguage.plain(
        ActionMessages.PURGED_ENTITIES,
        MessageArgument.untrusted("entities", ticket.getCount()),
        MessageArgument.untrusted("chunks", ticket.getTotalWork()),
        MessageArgument.untrusted("duration", Form.duration(ticket.getDuration(), 1))
    );
  }

  @Override
  public void workOn(ActionTicket<Params> ticket) {
    workOnAsync(ticket);
  }

  private void workOnAsync(ActionTicket<Params> ticket) {
    List<Chunk> chunks = pullChunks(ticket, React.controller(ActionController.class).getActionSpeedMultiplier());

    if (ticket.getTotalWork() <= 1 && ticket.getParams().getArea().getChunks() != null) {
      ticket.setTotalWork(Math.max(1, ticket.getParams().getArea().getChunks().size() + chunks.size()));
    }

    if (!chunks.isEmpty()) {
      for (Chunk chunk : chunks) {
        purgeChunkFolia(chunk, ticket.getParams());
      }
      ticket.addWork(chunks.size());
    }

    ticket.setCount(ticket.getParams().getPurgedEntities().get());
    if (chunks.isEmpty() && ticket.getParams().getInFlightChunks().get() <= 0) {
      ticket.complete();
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


  private void purge(Entity entity) {
    int delay = (int) (20 * Math.random());
    if (!J.runEntity(entity, () -> React.kill(entity, randomDelay), delay)) {
      React.kill(entity, randomDelay);
    }
  }

  private void purgeChunkFolia(Chunk chunk, Params params) {
    if (chunk == null || chunk.getWorld() == null || params == null) {
      return;
    }

    World world = chunk.getWorld();
    int chunkX = chunk.getX();
    int chunkZ = chunk.getZ();
    params.getInFlightChunks().incrementAndGet();
    boolean scheduled = J.runChunk(world, chunkX, chunkZ, () -> {
      try {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          return;
        }

        Chunk loadedChunk = world.getChunkAt(chunkX, chunkZ);
        int removed = 0;
        for (Entity entity : loadedChunk.getEntities()) {
          if (!params.getEntityFilter().allows(entity.getType())) {
            continue;
          }

          if (StackExclusion.isExcluded(entity)) {
            continue;
          }

          purge(entity);
          removed++;
        }

        if (removed > 0) {
          params.getPurgedEntities().addAndGet(removed);
        }
      } finally {
        params.getInFlightChunks().decrementAndGet();
      }
    });

    if (!scheduled) {
      params.getInFlightChunks().decrementAndGet();
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
    @art.arcane.react.util.project.config.ConfigDoc(value = "Area selection used by purge entities when choosing target chunks or entities.", impact = "Choose a tighter area for safer, local actions or a wider area for broader remediation.")
    private AreaActionParams area = AreaActionParams.builder().build();
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Filter definition for entity filter used by purge entities.", impact = "Narrow this list to target fewer cases, or broaden it to include more matching entries.")
    private FilterParams<EntityType> entityFilter = FilterParams.<EntityType>builder().build();
    @Builder.Default
    private transient AtomicInteger inFlightChunks = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger purgedEntities = new AtomicInteger(0);

    public Params withWorld(World world) {
      area.setWorld(WorldIdentity.serialize(world));
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
