package com.volmit.react.util.world;

import com.volmit.react.util.cache.Cache;
import com.volmit.react.util.data.B;
import lombok.AllArgsConstructor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Attachable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Hangable;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.AmethystCluster;
import org.bukkit.block.data.type.Bamboo;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.BigDripleaf;
import org.bukkit.block.data.type.CaveVines;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.DecoratedPot;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Fire;
import org.bukkit.block.data.type.RedstoneWallTorch;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.block.data.type.SeaPickle;
import org.bukkit.block.data.type.Sign;
import org.bukkit.block.data.type.Snow;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

@AllArgsConstructor
public class FastWorld {
    public static boolean needsUpdating(BlockData data, BlockFace face) {
        return (data instanceof Bisected b && ((b.getHalf().equals(Bisected.Half.TOP) && face.equals(BlockFace.UP)) || (b.getHalf().equals(Bisected.Half.BOTTOM) && face.equals(BlockFace.DOWN))))
            || (data instanceof FaceAttachable f && getAttachedFace(face).equals(f.getAttachedFace()))
            || data instanceof Attachable
            || data instanceof Levelled
            || data instanceof Snow && face.equals(BlockFace.UP)
            || data instanceof Hangable && face.equals(BlockFace.DOWN)
            || data instanceof MultipleFacing
            || data instanceof Rail && face.equals(BlockFace.UP)
            || data instanceof Bamboo && face.equals(BlockFace.UP)
            || data instanceof AmethystCluster && face.equals(BlockFace.UP)
            || data instanceof Bed
            || data instanceof BigDripleaf
            || data instanceof CaveVines
            || data.getMaterial().equals(Material.TORCH)
            || data.getMaterial().equals(Material.SOUL_TORCH)
            || data.getMaterial().equals(Material.REDSTONE_TORCH)
            || data.getMaterial().equals(Material.SOUL_WALL_TORCH)
            || data.getMaterial().equals(Material.WALL_TORCH)
            || data.getMaterial().equals(Material.REDSTONE_WALL_TORCH)
            || data instanceof RedstoneWire && face.equals(BlockFace.UP)
            || data instanceof Repeater && face.equals(BlockFace.UP)
            || data instanceof Comparator && face.equals(BlockFace.UP)
            || data instanceof Door && (face.equals(BlockFace.UP) || face.equals(BlockFace.DOWN))
            || data instanceof Sign

            ;
    }

    public static FaceAttachable.AttachedFace getAttachedFace(BlockFace face) {
        if(face.equals(BlockFace.UP)) {
            return FaceAttachable.AttachedFace.FLOOR;
        }

        if(face.equals(BlockFace.DOWN)) {
            return FaceAttachable.AttachedFace.CEILING;
        }

        return FaceAttachable.AttachedFace.WALL;
    }

    public static boolean shouldUpdate(Block block) {
        return needsUpdating(block.getRelative(BlockFace.UP).getBlockData(), BlockFace.UP)||
            needsUpdating(block.getRelative(BlockFace.DOWN).getBlockData(), BlockFace.DOWN)||
            needsUpdating(block.getRelative(BlockFace.NORTH).getBlockData(), BlockFace.NORTH)||
            needsUpdating(block.getRelative(BlockFace.SOUTH).getBlockData(), BlockFace.SOUTH)||
            needsUpdating(block.getRelative(BlockFace.EAST).getBlockData(), BlockFace.EAST)||
            needsUpdating(block.getRelative(BlockFace.WEST).getBlockData(), BlockFace.WEST);
    }


    public static void breakNaturally(Block block) {
        breakNaturally(block, true);
    }

    public static void breakNaturally(Block block, boolean fast) {
        if(!fast || shouldUpdate(block)) {
            block.breakNaturally();
        }

        else {
            Collection<ItemStack> drops = block.getDrops();
            block.setBlockData(B.getAir(), false);
            for(ItemStack i : drops) {
                block.getWorld().dropItemNaturally(block.getLocation(), i);
            }
        }
    }

    public static void set(Block block, BlockData data) {
        block.setBlockData(data, shouldUpdate(block));
    }

    public static void set(Block block, BlockData data, boolean allowFast) {
        block.setBlockData(data, !allowFast || shouldUpdate(block));
    }
}
