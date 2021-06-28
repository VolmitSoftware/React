package com.volmit.react.nms;

import com.volmit.react.Config;
import com.volmit.react.util.P;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.protocol.game.PacketPlayOutCollect;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.chunk.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import primal.bukkit.world.MaterialBlock;

public class NMSBinding17 extends NMSBinding {
    public NMSBinding17(String packageVersion) {
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
        net.minecraft.server.level.WorldServer w = ((CraftWorld) l.getWorld()).getHandle();
        Chunk chunk = w.getChunkAt(x >> 4, z >> 4);
        BlockPosition bp = new BlockPosition(x, y, z);
        chunk.setType(bp, net.minecraft.world.level.block.Block.getByCombinedId(m.getMaterial().getId() + (m.getData() << 12)), false);
    }

    @Override
    public void updateBlock(Block bfg) {
        if (!Config.USE_NMS) {
            return;
        }

        net.minecraft.world.level.block.Block b = org.bukkit.craftbukkit.v1_17_R1.util.CraftMagicNumbers.getBlock(bfg.getType());
        BlockPosition bp = new BlockPosition(bfg.getX(), bfg.getY(), bfg.getZ());
        CraftWorld w = (CraftWorld) bfg.getWorld();
        WorldServer v = w.getHandle();
        v.applyPhysics(bp, b);
    }

    @Override
    public void merge(Entity drop, Entity into) {
        for (Player i : drop.getWorld().getPlayers()) {
            if (P.isWithinViewDistance(i, drop.getLocation().getChunk())) {
                ((CraftPlayer) i).getHandle().b.sendPacket(new PacketPlayOutCollect(drop.getEntityId(), into.getEntityId(), 1));
            }
        }
    }
}
