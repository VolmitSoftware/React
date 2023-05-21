package com.volmit.react.core.nms;

import art.arcane.curse.Curse;
import art.arcane.curse.model.CursedComponent;
import art.arcane.curse.model.FuzzyMethod;
import com.volmit.react.React;
import com.volmit.react.util.data.B;
import com.volmit.react.util.scheduling.J;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityPlayer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public interface NMS {
    default Object getHandle(Player p) {
        return Curse.on(p).getTyped(classFor("net.minecraft.server.level.EntityPlayer"));
    }

    default Object getConnection(Player player) {
        return Curse.on(getHandle(player)).getTyped(classFor("net.minecraft.server.network.PlayerConnection"));
    }

    default CursedComponent curse(String className) {
        return Curse.on(classFor(className));
    }

    default Class<?> classFor(String canonical) {
        try {
            return Class.forName(canonical);
        } catch(ClassNotFoundException e) {
            return null;
        }
    }

    default void sendPacket(Player player, Object packet) {
        Curse.on(getConnection(player)).methodArgs(classFor("net.minecraft.network.protocol.Packet")).invoke(packet);
    }

    default int getBlockId(BlockData data) {
        return curse("net.minecraft.world.level.block.Block").fuzzyMethod(FuzzyMethod.builder()
                .returns(int.class)
                .parameter(classFor("net.minecraft.world.level.block.state.IBlockData"))
            .build()).orElseThrow().invoke(data);
    }

    default Object removeEntityPacket(int entity) {
        return curse("net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy")
            .construct(new IntArrayList(entity)).instance();
    }

    Object getEntityMetadataPacket(int eid, List<Object> items);

    Object getGravityProperty(boolean gravity);

    Object getEntityStateProperty(byte state);

    default void sendPacket(Block at, int radius, Object packet) {
        sendPacket(at.getLocation(), radius, packet);
    }

    default void sendPacket(Entity at, int radius, Object packet) {
        sendPacket(at.getLocation(), radius, packet);
    }

    default void sendPacket(Location at, int radius, Object packet) {
        for(Player i : at.getWorld().getPlayers()) {
            if(i.getLocation().distanceSquared(at) < radius * radius) {
                sendPacket(i, packet);
            }
        }
    }
}
