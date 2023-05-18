package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.math.RNG;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Reduces entity spawns / garbage by teleporting drops and xp from blocks and entities directly into your inventory
 */
public class FeatureFastDrops extends ReactFeature implements Listener {
    public static final String ID = "fast-drops";
    private boolean teleportBlockDrops = true;
    private boolean teleportBlockXP = true;
    private boolean teleportEntityDrops = true;
    private boolean teleportEntityXP = true;
    private boolean ignoreDistanceKills = true;

    public FeatureFastDrops() {
        super(ID);
    }

    @EventHandler(priority = EventPriority.MONITOR)
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
                        p.getWorld().dropItem(p.getLocation(), j);
                        dropped = true;
                    }

                    if(!dropped) {
                       p.playSound(e.getEntity(), Sound.ENTITY_ITEM_PICKUP,0.7f, 1f+ RNG.r.f(-0.2f, 0.2f));
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if(!teleportBlockXP && !teleportBlockDrops) {
            return;
        }

        if(!e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)) {
            return;
        }

        if(!e.isCancelled()) {
            if(teleportBlockDrops) {
                e.setDropItems(false);
            }

            int xp = teleportBlockXP ? e.getExpToDrop() : 0;

            if(teleportBlockXP) {
                e.setExpToDrop(0);
            }

            if(xp > 0 && teleportEntityXP) {
                e.getPlayer().giveExp(xp);
                e.getPlayer().playSound(e.getBlock().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,0.7f, 1f + RNG.r.f(-0.2f, 0.2f));
            }

            if(teleportBlockDrops) {
                for(ItemStack i : e.getBlock().getDrops(e.getPlayer().getInventory().getItemInMainHand(), e.getPlayer())) {
                    boolean dropped = false;
                    for(ItemStack j : e.getPlayer().getInventory().addItem(i).values()) {
                        e.getPlayer().getWorld().dropItem(e.getPlayer().getLocation(), j);
                        dropped = true;
                    }

                    if(!dropped) {
                        e.getPlayer().playSound(e.getBlock().getLocation(), Sound.ENTITY_ITEM_PICKUP,0.7f, 1f+ RNG.r.f(-0.2f, 0.2f));
                    }
                }
            }
        }
    }

    @Override
    public void onActivate() {
        React.instance.registerListener(this);
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
