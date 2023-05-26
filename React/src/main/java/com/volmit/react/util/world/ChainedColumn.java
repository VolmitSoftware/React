package com.volmit.react.util.world;

import com.volmit.react.React;
import com.volmit.react.content.tweak.TweakFastDrops;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ChainedColumn {
    private final Material type;
    private final Material drops;
    private final boolean underwater;
    private final TweakFastDrops fastDrops;

    public ChainedColumn(Material type, Material drops, boolean underwater) {
        this.type = type;
        this.drops = drops;
        this.underwater = underwater;
        fastDrops = React.tweak(TweakFastDrops.class);
    }

    public boolean is(Block block) {
        return block.getType().equals(type);
    }

    public boolean isEmpty(Block block) {
        return underwater ? block.getType().equals(Material.WATER) : block.isEmpty();
    }

    public void trigger(Block block, int max) {
        trigger(null, block, max);
    }

    public void trigger(Player player, Block block, int max) {
        int d = 0;
        boolean latched = false;

        int maxh = Math.min(block.getWorld().getMaxHeight(), block.getY()+max)-1;
        for(int i = block.getY(); i < maxh; i++) {
            Block b = block.getWorld().getBlockAt(block.getX(), i, block.getZ());
            if(b.getType().equals(type)) {
                latched = true;
                d++;
                Block next = block.getWorld().getBlockAt(block.getX(), i+1, block.getZ());
                boolean hasNext = next.getType().equals(type);
                b.setType(underwater ? Material.WATER : Material.AIR, !hasNext || i == maxh-1);
            }

            else if(latched) {
                break;
            }
        }

        if(d > 0) {
            ItemStack item = new ItemStack(drops, d);

            if(player != null) {
                fastDrops.giveItem(block.getLocation().add(0.5, 0.5, 0.5), player, item);
            }

            else {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0, 0.5), item);
            }
        }
    }
}
