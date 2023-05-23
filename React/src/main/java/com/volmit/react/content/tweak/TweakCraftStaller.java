package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.CraftingInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TweakCraftStaller extends ReactTweak implements Listener {
    public static final String ID = "craft-staller";
    private static final int MAX_CRAFTS_PER_MINUTE = 100; // Set this to whatever value you think is reasonable
    private Map<UUID, Pair<Long, Integer>> playerCraftCounts = new HashMap<>();

    /**
     * So here is an explanation of the issue:
     * The whole issue is that the server does crafting on the main thread.
     * This means that if you have a lot of crafting going on, the server will lag.
     * normally, this is not an issue, but if you have a lot of crafting going on, it can be.
     * and people can setup macros to craft a lot of items at once.
     * a single person can lag the server this way.
     **/

    public TweakCraftStaller() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @Override
    public void onDeactivate() {
        playerCraftCounts.clear();
    }

    @Override
    public int getTickInterval() {
        return 60000; // 1 minute in milliseconds
    }

    @Override
    public void onTick() {
        playerCraftCounts.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraft(CraftItemEvent e) {
        React.info("Craft event fired");
        UUID playerId = e.getWhoClicked().getUniqueId();
        long currentMinute = System.currentTimeMillis() / (60 * 1000);
        Pair<Long, Integer> craftCount = playerCraftCounts.getOrDefault(playerId, Pair.of(currentMinute, 0));

        if (craftCount.getLeft() == currentMinute && craftCount.getRight() < MAX_CRAFTS_PER_MINUTE) {
            craftCount = Pair.of(currentMinute, craftCount.getRight() + 1);
            playerCraftCounts.put(playerId, craftCount);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getClickedInventory() instanceof CraftingInventory)) {
            return;
        }

        UUID playerId = e.getWhoClicked().getUniqueId();
        long currentMinute = System.currentTimeMillis() / (60 * 1000);
        Pair<Long, Integer> craftCount = playerCraftCounts.getOrDefault(playerId, Pair.of(currentMinute, 0));

        if (craftCount.getLeft() == currentMinute && craftCount.getRight() >= MAX_CRAFTS_PER_MINUTE) {
            e.setCancelled(true);
        }
    }
}
