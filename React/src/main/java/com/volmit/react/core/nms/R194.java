package com.volmit.react.core.nms;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.util.data.B;
import com.volmit.react.util.scheduling.J;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy;
import net.minecraft.network.protocol.game.PacketPlayOutEntityEffect;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.EntityFallingBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_19_R2.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class R194 {
    private static int eids = 0;

    public static void sendPacket(Player p, Object o) {
        ((CraftPlayer) p).getHandle().b.a((Packet<?>) o);
    }

    public static void removeEntity(Player player, int entity) {
        sendPacket(player, new PacketPlayOutEntityDestroy(entity));
    }

    public static byte entityProperiesBitMask(
        boolean onFire,
        boolean crouching,
        boolean ridingLegacy,
        boolean sprinting,
        boolean swimming,
        boolean invisible,
        boolean glowing,
        boolean flyingElytra) {
        return (byte) (
            (onFire ? 0x01 : 0) |
            (crouching ? 0x02 : 0) |
            (ridingLegacy ? 0x04 : 0) |
            (sprinting ? 0x08 : 0) |
            (swimming ? 0x10 : 0) |
            (invisible ? 0x20 : 0) |
            (glowing ? 0x40 : 0) |
            (flyingElytra ? 0x80 : 0)
        );
    }

    public static void glow(Player player, org.bukkit.block.Block block, ChatColor color, int ticks) {
        BlockData rbd = block.getBlockData();
        player.sendBlockChange(block.getLocation(), B.get("barrier"));
        BlockData b = block.getType().isAir() ? B.get("glass") : rbd;
        int id = sendFallingBlock(player, block.getLocation().add(0.5, 0, 0.5), b);
        UUID t = uuid(id);
        try {
            React.instance.getGlowingEntities().setGlowing(id, t.toString(), player, color);
        }

        catch(Throwable e) {
            e.printStackTrace();
        }

        J.ss(() -> {
            player.sendBlockChange(block.getLocation(), rbd);
            removeEntity(player, id);
        }, ticks);
    }

    public static int sendFallingBlock(Player player, Location location, BlockData data) {
        int id = sendSpawnEntity(player, EntityType.FALLING_BLOCK, location, getBlockId(data), 0f);
        sendMetadata(player, id, List.of(
            gravity(false),
            entityState(entityProperiesBitMask(false, false,
                false, false, false,
                true, true, false))
            ));
        return id;
    }

    public static int getBlockId(BlockData data) {
        return Block.i(Curse.on(data).get("state"));
    }

    public static void sendMetadata(Player player, int eid, List<DataWatcher.b<?>> items) {
        sendPacket(player, new PacketPlayOutEntityMetadata(eid, items));
    }

    public static DataWatcher.b<?> entityState(byte masked){
        return new DataWatcher.b<>(0, DataWatcherRegistry.a, masked);
    }

    public static DataWatcher.b<?> gravity(boolean gravity) {
        return new DataWatcher.b<>(5, DataWatcherRegistry.j, !gravity);
    }

    public static UUID uuid(int eid) {
        return UUID.nameUUIDFromBytes(new byte[]{
            (byte) (eid & 0xff),
            (byte) ((eid >> 8) & 0xff),
            (byte) ((eid >> 16) & 0xff),
            (byte) ((eid >> 24) & 0xff),
        });
    }

    public static int sendSpawnEntity(Player player, EntityType entityType, Location location, int data, float headYaw) {
        int eid = nextId(location.getWorld());
        PacketPlayOutSpawnEntity p = new PacketPlayOutSpawnEntity(eid, uuid(eid),
            location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(),
            EntityTypes.a(entityType.getKey().getKey()).get(),
            data, new Vec3D(0,0,0), headYaw);

        sendPacket(player, p);

        return eid;
    }

    public static int nextId(World world) {
        return 100000 + eids++;
    }
}
