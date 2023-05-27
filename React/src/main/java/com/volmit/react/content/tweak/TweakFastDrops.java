package com.volmit.react.content.tweak;

import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.math.RNG;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
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
public class TweakFastDrops extends ReactTweak implements Listener {
    public static final String ID = "fast-drops";
    private boolean teleportBlockDrops = true;
    private boolean teleportBlockXP = true;
    private boolean teleportEntityDrops = true;
    private boolean teleportEntityXP = true;
    private boolean allowContainerDrops = false;
    private boolean ignoreDistanceKills = true;

    public TweakFastDrops() {
        super(ID);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDeathEvent e) {
        if(!teleportEntityXP && !teleportEntityDrops) {
            return;
        }

        Player p = e.getEntity().getKiller();

        if(p != null) {
            if(p.getGameMode().equals(GameMode.CREATIVE)) {
                return;
            }

            if(p.getLocation().distanceSquared(e.getEntity().getLocation()) > 7 * 7) {
                return;
            }

            int xp = teleportEntityXP ? e.getDroppedExp() : 0;

            if(teleportEntityDrops) {
                e.setDroppedExp(0);

                if(xp > 0) {
                    p.giveExp(xp);
                    p.playSound(e.getEntity().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,0.7f, 1f + RNG.r.f(-0.2f, 0.2f));
                }
            }

            if(teleportBlockDrops) {
                List<ItemStack> drops = new ArrayList<>(e.getDrops());
                e.getDrops().clear();
                for(ItemStack i : drops) {
                    boolean dropped = false;
                    for(ItemStack j : p.getInventory().addItem(i).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), j);
                        dropped = true;
                    }

                    if(!dropped) {
                       p.playSound(e.getEntity(), Sound.ENTITY_ITEM_PICKUP,0.7f, 1f+ RNG.r.f(-0.2f, 0.2f));
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockDropItemEvent e) {
        if(!allowContainerDrops && e.getBlock().getState() instanceof InventoryHolder) {
            return;
        }

        if(!e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)) {
            return;
        }

        if(teleportBlockDrops) {
            e.setCancelled(true);

            for(Item i : e.getItems()) {
                boolean dropped = false;
                for(ItemStack j : e.getPlayer().getInventory().addItem(i.getItemStack()).values()) {
                    e.getPlayer().getWorld().dropItemNaturally(i.getLocation(), j);
                    dropped = true;
                }

                if(!dropped) {
                    e.getPlayer().playSound(e.getBlock().getLocation(), Sound.ENTITY_ITEM_PICKUP,0.7f, 1f+ RNG.r.f(-0.2f, 0.2f));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockBreakEvent e) {
        if(!teleportBlockXP) {
            return;
        }

        if(!e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)) {
            return;
        }

        int xp = teleportBlockXP ? e.getExpToDrop() : 0;

        if(teleportBlockXP) {
            e.setExpToDrop(0);
        }

        if(xp > 0 && teleportEntityXP) {
            e.getPlayer().giveExp(xp);
            e.getPlayer().playSound(e.getBlock().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,0.7f, 1f + RNG.r.f(-0.2f, 0.2f));
        }
    }

    public void giveXP(Location at, Player player, int xp) {
        if(player.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }

        if(isEnabled()) {
            player.giveExp(xp);
            player.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP,0.7f, 1f + RNG.r.f(-0.2f, 0.2f));
        } else {
            ExperienceOrb orb = (ExperienceOrb) at.getWorld().spawnEntity(at, org.bukkit.entity.EntityType.EXPERIENCE_ORB);
            orb.setExperience(xp);
        }
    }

    public void giveItem(Location at, Player player, ItemStack item) {
        if(player.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }

        if(isEnabled()) {
            boolean dropped = false;
            for(ItemStack j : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(at, j);
                dropped = true;
            }

            if(!dropped) {
                player.playSound(at, Sound.ENTITY_ITEM_PICKUP,0.7f, 1f+ RNG.r.f(-0.2f, 0.2f));
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
