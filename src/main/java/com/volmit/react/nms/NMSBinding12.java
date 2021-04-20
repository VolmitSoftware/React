package com.volmit.react.nms;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.util.P;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.PacketPlayOutCollect;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import primal.bukkit.world.MaterialBlock;

public class NMSBinding12 extends NMSBinding {
    public NMSBinding12(String packageVersion) {
        super(packageVersion);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBlock(Location l, MaterialBlock m) {
        if (!Config.USE_NMS) {
            l.getBlock().setTypeIdAndData(m.getMaterial().getId(), m.getData(), false);
            return;
        }

        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();
        net.minecraft.server.v1_12_R1.World w = ((CraftWorld) l.getWorld()).getHandle();
        net.minecraft.server.v1_12_R1.Chunk chunk = w.getChunkAt(x >> 4, z >> 4);
        BlockPosition bp = new BlockPosition(x, y, z);
        int combined = m.getMaterial().getId() + (m.getData() << 12);
        IBlockData ibd = net.minecraft.server.v1_12_R1.Block.getByCombinedId(combined);
        chunk.a(bp, ibd);
        Gate.sendBlockChange(l);
    }

    @Override
    public void updateBlock(Block bfg) {
        if (!Config.USE_NMS) {
            return;
        }

        net.minecraft.server.v1_12_R1.Block b = org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers.getBlock(bfg);
        net.minecraft.server.v1_12_R1.BlockPosition bp = new net.minecraft.server.v1_12_R1.BlockPosition(bfg.getX(), bfg.getY(), bfg.getZ());
        org.bukkit.craftbukkit.v1_12_R1.CraftWorld w = (org.bukkit.craftbukkit.v1_12_R1.CraftWorld) bfg.getWorld();
        net.minecraft.server.v1_12_R1.World v = w.getHandle();
        v.applyPhysics(bp, b, true);
    }

    @Override
    public void merge(Entity drop, Entity into) {
        for (Player i : drop.getWorld().getPlayers()) {
            if (P.isWithinViewDistance(i, drop.getLocation().getChunk())) {
                ((CraftPlayer) i).getHandle().playerConnection.sendPacket(new PacketPlayOutCollect(drop.getEntityId(), into.getEntityId(), 1));
            }
        }
    }
}
