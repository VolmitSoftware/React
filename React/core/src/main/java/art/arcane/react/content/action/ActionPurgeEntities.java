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

import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ActionMessages;
import art.arcane.react.model.AreaActionParams;
import art.arcane.react.model.FilterParams;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Purge Entities action. Removes matching entities from selected chunks or worlds.")
public class ActionPurgeEntities extends ReactAction<ActionPurgeEntities.Params> {
  public static final String ID = "purge-entities";
  private List<String> blacklist = new ArrayList<>(List.of(
      "ENDER_DRAGON", "ITEM_DISPLAY", "PLAYER", "ARMOR_STAND", "ITEM_FRAME", "PAINTING", "LEASH_KNOT",
      "MINECART", "CHEST_MINECART", "COMMAND_BLOCK_MINECART", "FURNACE_MINECART",
      "HOPPER_MINECART", "SPAWNER_MINECART", "TNT_MINECART",
      "ACACIA_BOAT", "BAMBOO_RAFT", "BIRCH_BOAT", "CHERRY_BOAT", "DARK_OAK_BOAT",
      "JUNGLE_BOAT", "MANGROVE_BOAT", "OAK_BOAT", "PALE_OAK_BOAT", "SPRUCE_BOAT",
      "ACACIA_CHEST_BOAT", "BAMBOO_CHEST_RAFT", "BIRCH_CHEST_BOAT", "CHERRY_CHEST_BOAT",
      "DARK_OAK_CHEST_BOAT", "JUNGLE_CHEST_BOAT", "MANGROVE_CHEST_BOAT", "OAK_CHEST_BOAT",
      "PALE_OAK_CHEST_BOAT", "SPRUCE_CHEST_BOAT",
      "FALLING_BLOCK", "ITEM", "EXPERIENCE_ORB", "FISHING_BOBBER",
      "TNT", "SPLASH_POTION", "EXPERIENCE_BOTTLE", "ENDER_PEARL",
      "EYE_OF_ENDER", "FIREWORK_ROCKET", "LIGHTNING_BOLT", "SHULKER_BULLET",
      "SMALL_FIREBALL", "SNOWBALL", "SPECTRAL_ARROW", "SPLASH_POTION",
      "EXPERIENCE_BOTTLE"
  ));
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether purge entities applies default blacklist.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean defaultBlacklist = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Countdown baseline in seconds before purge entities removes matching entities.", impact = "Higher values give players more warning time; lower values purge sooner.")
  private int secondsToPurge = 5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Protects player-named entities from purge entities.", impact = "Keep enabled to exclude entities with a custom name; disable it to make named entities eligible for removal.")
  private boolean protectNamedEntities = true;

  public ActionPurgeEntities() {
    super(ID);
  }

  List<ChunkRef> pullChunks(ActionTicket<Params> ticket, int max) {
    return ticket.getParams().getArea().popChunks(max);
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
    ActionController actions = ReactComponents.actions();
    List<ChunkRef> chunks = pullChunks(ticket, actions == null ? 128 : actions.getActionSpeedMultiplier());

    if (ticket.getTotalWork() <= 1) {
      ticket.setTotalWork(Math.max(1, ticket.getParams().getArea().estimatedTotalWork()));
    }

    if (!chunks.isEmpty()) {
      for (ChunkRef chunk : chunks) {
        purgeChunkFolia(chunk, ticket.getParams());
      }
      ticket.addWork(chunks.size());
    }

    ticket.setCount(ticket.getParams().getPurgedEntities().get());
    if (chunks.isEmpty()
        && ticket.getParams().getInFlightChunks().get() <= 0
        && !ticket.getParams().getArea().isSelectionPending()) {
      ticket.setTotalWork(Math.max(1, ticket.getWork()));
      ticket.complete();
    }
  }

  @Override
  public Params getDefaultParams() {
    return Params.builder()
        .entityFilter(FilterParams.<String>builder()
            .types(blacklist)
            .blacklist(defaultBlacklist)
            .build())
        .protectNamedEntities(protectNamedEntities)
        .build();
  }


  private void purge(GameEntity entity, Params params) {
    EntityHost host = entities();
    int delay = (int) (20 * Math.random());
    Runnable removal = () -> {
      if (canPurge(entity, params) && host.allows(entity, ReactOperation.PURGE)) {
        host.kill(entity, purgeDelaySeconds());
      }
    };
    if (!J.runEntity(entity.handle(), removal, delay)) {
      removal.run();
    }
  }

  private int purgeDelaySeconds() {
    return Math.max(0, secondsToPurge - 1) + ThreadLocalRandom.current().nextInt(3);
  }

  boolean canPurge(GameEntity entity, Params params) {
    EntityHost host = entities();
    return entity != null
        && host != null
        && entity.isValid()
        && !"ENDER_DRAGON".equals(entity.type())
        && params != null
        && params.getEntityFilter().allows(entity.type())
        && !host.protectsNamed(entity, params.isProtectNamedEntities());
  }

  private void purgeChunkFolia(ChunkRef chunk, Params params) {
    if (chunk == null || params == null) {
      return;
    }

    ReactPlatform platform = ReactPlatforms.getOrNull();
    WorldHost worlds = platform == null ? null : platform.worldHost().orElse(null);
    EntityHost host = entities();
    ReactWorld world = worlds == null ? null : worlds.world(chunk.worldId()).orElse(null);
    if (world == null || host == null) {
      return;
    }
    int chunkX = chunk.chunkX();
    int chunkZ = chunk.chunkZ();
    params.getInFlightChunks().incrementAndGet();
    boolean scheduled = J.runChunk(world, chunkX, chunkZ, () -> {
      try {
        int removed = 0;
        for (GameEntity entity : host.chunkEntities(world, chunkX, chunkZ)) {
          if (!canPurge(entity, params)) {
            continue;
          }

          if (!host.allows(entity, ReactOperation.PURGE)) {
            continue;
          }

          purge(entity, params);
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

  private static EntityHost entities() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.entityHost().orElse(null);
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
    private FilterParams<String> entityFilter = FilterParams.<String>builder().build();
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Protects player-named entities from purge entities.", impact = "Keep enabled to exclude entities with a custom name; disable it to make named entities eligible for removal.")
    private boolean protectNamedEntities = true;
    @Builder.Default
    private transient AtomicInteger inFlightChunks = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger purgedEntities = new AtomicInteger(0);

    public Params withWorld(String worldKey) {
      area.setWorld(worldKey);
      area.setAllChunks(true);
      return this;
    }

    public Params addRadius(ReactWorld world, int x, int z, int radius) {
      area.selectLoadedRadius(world, x, z, radius);
      return this;
    }
  }
}
