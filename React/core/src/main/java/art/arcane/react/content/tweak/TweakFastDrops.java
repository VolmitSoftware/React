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

package art.arcane.react.content.tweak;

import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.platform.DropHost;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Reduces entity spawns / garbage by teleporting drops and xp from blocks and
 * entities directly into your inventory
 */
@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Drops tweak. Routes drops and XP directly to players to reduce ground-item buildup and pickup churn.")
public class TweakFastDrops extends ReactTweak {
  public static final String ID = "fast-drops";
  private static final String EXPERIENCE_SOUND = "minecraft:entity.experience_orb.pickup";
  private static final String ITEM_SOUND = "minecraft:entity.item.pickup";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast drops applies teleport block drops.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean teleportBlockDrops = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast drops applies teleport block xp.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean teleportBlockXP = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast drops applies teleport entity drops.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean teleportEntityDrops = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast drops applies teleport entity xp.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean teleportEntityXP = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Allows container drops in fast drops.", impact = "Enable this to permit the behavior; disable it to block or throttle that path.")
  private boolean allowContainerDrops = false;
  private transient final List<Subscription> subscriptions = new ArrayList<>();

  public TweakFastDrops() {
    super(ID);
  }

  @Override
  public synchronized void onActivate() {
    cancelSubscriptions();
    DropHost host = drops();
    subscriptions.add(host.onEntityDeath(this::onEntityDeath));
    subscriptions.add(host.onBlockDrops(this::onBlockDrops));
    subscriptions.add(host.onBlockBreak(this::onBlockBreak));
  }

  @Override
  public synchronized void onDeactivate() {
    cancelSubscriptions();
  }

  private void cancelSubscriptions() {
    for (Subscription subscription : subscriptions) {
      subscription.cancel();
    }
    subscriptions.clear();
  }

  void onEntityDeath(DropHost.EntityDeath e) {
    if (!teleportEntityXP && !teleportEntityDrops) {
      return;
    }

    ReactPlayer p = e.killer();

    if (p != null) {
      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(p.handle())) {
        return;
      }

      DropHost host = drops();
      if (host.isCreative(p)) {
        return;
      }

      if (e.killerDistanceSquared() > 7 * 7) {
        return;
      }

      if (teleportEntityXP) {
        int xp = e.droppedExperience();
        e.setDroppedExperience(0);

        if (xp > 0) {
          host.awardExperience(p, xp);
          host.playSound(p, EXPERIENCE_SOUND, 0.7f, 1f + RNG.r.f(-0.2f, 0.2f));
        }
      }

      if (teleportEntityDrops) {
        for (Object i : e.takeDrops()) {
          if (!host.give(p, host.copyStack(i), null)) {
            host.playSound(p, ITEM_SOUND, 0.7f, 1f + RNG.r.f(-0.2f, 0.2f));
          }
        }
      }
    }
  }

  void onBlockDrops(DropHost.BlockDrops e) {
    if (!allowContainerDrops && e.fromContainer()) {
      return;
    }

    DropHost host = drops();
    ReactPlayer player = e.player();
    if (!host.isSurvival(player)) {
      return;
    }

    if (teleportBlockDrops) {
      for (DropHost.DroppedItem i : new ArrayList<>(e.items())) {
        Object stack = i.copyStack();
        if (!i.remove()) {
          continue;
        }

        if (!host.give(player, stack, i.dropLocation())) {
          host.playSound(player, ITEM_SOUND, 0.7f, 1f + RNG.r.f(-0.2f, 0.2f));
        }
      }
    }
  }

  void onBlockBreak(DropHost.BlockBreak e) {
    if (!teleportBlockXP) {
      return;
    }

    DropHost host = drops();
    ReactPlayer player = e.player();
    if (!host.isSurvival(player)) {
      return;
    }

    int xp = e.experience();
    e.setExperience(0);

    if (xp > 0) {
      host.awardExperience(player, xp);
      host.playSound(player, EXPERIENCE_SOUND, 0.7f, 1f + RNG.r.f(-0.2f, 0.2f));
    }
  }

  public void giveItem(Object at, ReactPlayer player, Object item) {
    DropHost host = drops();
    if (host.isCreative(player)) {
      return;
    }

    if (isEnabled()) {
      if (!host.give(player, item, at)) {
        host.playSound(player, ITEM_SOUND, 0.7f, 1f + RNG.r.f(-0.2f, 0.2f));
      }
    } else {
      host.dropNaturally(player, at, item);
    }
  }

  private static DropHost drops() {
    return ReactPlatforms.get().dropHost().orElseThrow(() -> new IllegalStateException("No drop host on this platform"));
  }
}
