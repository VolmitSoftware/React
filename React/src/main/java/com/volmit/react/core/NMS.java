package com.volmit.react.core;

import art.arcane.curse.Curse;
import art.arcane.curse.model.CursedComponent;
import art.arcane.curse.model.FuzzyMethod;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

public class NMS {
    public static Object getHandle(Player p) {
        return Curse.on(p).get("handle");
    }

    public static Object blockPosition(int x, int y, int z) {
        return curse("net.minecraft.core.BlockPosition").construct(x, y, z);
    }

    public static Object blockPosition(Block block) {
        return blockPosition(block.getX(), block.getY(), block.getZ());
    }

    public static List<?> getTickList(World world) {
        return Curse.on(getWorldServer(world)).get("o");
    }

    public static Object getWorldServer(World world) {
        return Curse.on(world).get("handle");
    }

    public static Object getConnection(Player player) {
        return Curse.on(getHandle(player)).fuzzyField(classFor("net.minecraft.server.network.PlayerConnection")).get().get();
    }

    public static CursedComponent curse(String className) {
        return Curse.on(classFor(className));
    }

    public static Class<?> classFor(String canonical) {
        try {
            return Class.forName(canonical);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static void sendPacket(Player player, Object packet) {
        Curse.on(getConnection(player)).methodArgs(classFor("net.minecraft.network.protocol.Packet")).invoke(packet);
    }

    public static int getBlockId(BlockData data) {
        return curse("net.minecraft.world.level.block.Block").fuzzyMethod(FuzzyMethod.builder()
                .returns(int.class)
                .parameter(classFor("net.minecraft.world.level.block.state.IBlockData"))
                .build()).orElseThrow().invoke(data);
    }

    public static Object removeEntityPacket(int entity) {
        return curse("net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy")
                .construct(new IntArrayList(entity)).instance();
    }

    public static Object collectPacket(int entity, int toCollect, int count) {
        return curse("net.minecraft.network.protocol.game.PacketPlayOutCollect")
                .construct(entity, toCollect, count).instance();
    }

    public static void sendPacket(Block at, int radius, Object packet) {
        sendPacket(at.getLocation(), radius, packet);
    }

    public static void sendPacket(Entity at, int radius, Object packet) {
        sendPacket(at.getLocation(), radius, packet);
    }

    public static void sendPacket(Location at, int radius, Object packet) {
        for (Player i : at.getWorld().getPlayers()) {
            if (i.getLocation().distanceSquared(at) < radius * radius) {
                sendPacket(i, packet);
            }
        }
    }
}
