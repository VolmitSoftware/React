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

import art.arcane.react.React;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.volmlib.util.math.RNG;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Reduces entity spawns / garbage by teleporting drops and xp from blocks and entities directly into your inventory
 */
@art.arcane.react.util.config.ConfigDescription("Configuration for Fast Drops tweak. Routes drops and XP directly to players to reduce ground-item buildup and pickup churn.")
public class TweakFastDrops extends ReactTweak implements Listener {
    public static final String ID = "fast-drops";
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether fast drops applies teleport block drops.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean teleportBlockDrops = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether fast drops applies teleport block xp.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean teleportBlockXP = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether fast drops applies teleport entity drops.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean teleportEntityDrops = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether fast drops applies teleport entity xp.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean teleportEntityXP = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Allows container drops in fast drops.", impact = "Enable this to permit the behavior; disable it to block or throttle that path.")
    private boolean allowContainerDrops = false;

    public TweakFastDrops() {
        super(ID);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDeathEvent e) {
        if (!teleportEntityXP && !teleportEntityDrops) {
            return;
        }

        Player p = e.getEntity().getKiller();

        if (p != null) {
            if (p.getGameMode().equals(GameMode.CREATIVE)) {
                return;
            }

            if (p.getLocation().distanceSquared(e.getEntity().getLocation()) > 7 * 7) {
                return;
            }

            int xp = teleportEntityXP ? e.getDroppedExp() : 0;

            if (teleportEntityDrops) {
                e.setDroppedExp(0);

                if (xp > 0) {
                    p.giveExp(xp);
                    React.audiences.player(p).playSound(Sound.sound(
                            Key.key("minecraft:entity.experience_orb.pickup"),
                            Sound.Source.PLAYER,
                            0.7f,
                            1f + RNG.r.f(-0.2f, 0.2f)
                    ));
                }
            }

            if (teleportBlockDrops) {
                List<ItemStack> drops = new ArrayList<>(e.getDrops());
                e.getDrops().clear();
                for (ItemStack i : drops) {
                    boolean dropped = false;
                    for (ItemStack j : p.getInventory().addItem(i).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), j);
                        dropped = true;
                    }

                    if (!dropped) {
                        React.audiences.player(p).playSound(Sound.sound(
                                Key.key("minecraft:entity.item.pickup"),
                                Sound.Source.PLAYER,
                                0.7f,
                                1f + RNG.r.f(-0.2f, 0.2f)
                        ));
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockDropItemEvent e) {
        if (!allowContainerDrops && e.getBlock().getState() instanceof InventoryHolder) {
            return;
        }

        if (!e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)) {
            return;
        }

        if (teleportBlockDrops) {
            e.setCancelled(true);

            for (Item i : e.getItems()) {
                boolean dropped = false;
                for (ItemStack j : e.getPlayer().getInventory().addItem(i.getItemStack()).values()) {
                    e.getPlayer().getWorld().dropItemNaturally(i.getLocation(), j);
                    dropped = true;
                }

                if (!dropped) {
                    React.audiences.player(e.getPlayer()).playSound(Sound.sound(
                            Key.key("minecraft:entity.item.pickup"),
                            Sound.Source.PLAYER,
                            0.7f,
                            1f + RNG.r.f(-0.2f, 0.2f)
                    ));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (!teleportBlockXP) {
            return;
        }

        if (!e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)) {
            return;
        }

        int xp = teleportBlockXP ? e.getExpToDrop() : 0;

        if (teleportBlockXP) {
            e.setExpToDrop(0);
        }

        if (xp > 0 && teleportEntityXP) {
            e.getPlayer().giveExp(xp);
            React.audiences.player(e.getPlayer()).playSound(Sound.sound(
                    Key.key("minecraft:entity.experience_orb.pickup"),
                    Sound.Source.PLAYER,
                    0.7f,
                    1f + RNG.r.f(-0.2f, 0.2f)
            ));
        }
    }



    public void giveXP(Location at, Player player, int xp) {
        if (player.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }

        if (isEnabled()) {
            player.giveExp(xp);
            React.audiences.player(player).playSound(Sound.sound(
                    Key.key("minecraft:entity.experience_orb.pickup"),
                    Sound.Source.PLAYER,
                    0.7f,
                    1f + RNG.r.f(-0.2f, 0.2f)
            ));
        } else {
            ExperienceOrb orb = (ExperienceOrb) at.getWorld().spawnEntity(at, org.bukkit.entity.EntityType.EXPERIENCE_ORB);
            orb.setExperience(xp);
        }
    }

    public void giveItem(Location at, Player player, ItemStack item) {
        if (player.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }

        if (isEnabled()) {
            boolean dropped = false;
            for (ItemStack j : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(at, j);
                dropped = true;
            }

            if (!dropped) {
                React.audiences.player(player).playSound(Sound.sound(
                        Key.key("minecraft:entity.item.pickup"),
                        Sound.Source.PLAYER,
                        0.7f,
                        1f + RNG.r.f(-0.2f, 0.2f)
                ));
            }
        } else {
            player.getWorld().dropItemNaturally(at, item);
        }
    }

    @Override
    public void onActivate() {

    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
