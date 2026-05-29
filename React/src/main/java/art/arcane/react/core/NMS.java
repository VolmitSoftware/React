/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.core;

import art.arcane.curse.Curse;
import art.arcane.curse.model.CursedComponent;
import art.arcane.curse.model.CursedField;
import art.arcane.curse.model.FuzzyMethod;
import art.arcane.react.React;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

public class NMS {
  private static final String[] BLOCK_POSITION_CLASS_NAMES = {"net.minecraft.core.BlockPos", "net.minecraft.core.BlockPosition"};
  private static final String[] CONNECTION_CLASS_NAMES = {"net.minecraft.server.network.ServerGamePacketListenerImpl", "net.minecraft.server.network.PlayerConnection"};
  private static final String[] PACKET_CLASS_NAMES = {"net.minecraft.network.protocol.Packet"};
  private static final String[] BLOCK_DATA_CLASS_NAMES = {"net.minecraft.world.level.block.state.BlockState", "net.minecraft.world.level.block.state.IBlockData"};
  private static final String[] REMOVE_ENTITY_PACKET_CLASS_NAMES = {"net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket", "net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy"};
  private static final String[] COLLECT_PACKET_CLASS_NAMES = {"net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket", "net.minecraft.network.protocol.game.PacketPlayOutCollect"};
  private static boolean collectPacketDisabled;
  private static boolean collectPacketWarningIssued;

  public static Object getHandle(Player p) {
    return Curse.on(p).get("handle");
  }

  public static Object blockPosition(int x, int y, int z) {
    return curse(BLOCK_POSITION_CLASS_NAMES).construct(x, y, z);
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
    Class<?> connectionClass = classFor(CONNECTION_CLASS_NAMES);
    if (connectionClass == null) {
      throw new IllegalStateException("Unable to resolve the player connection class.");
    }

    CursedField connectionField = (CursedField) Curse.on(getHandle(player))
        .fuzzyField(connectionClass)
        .orElseThrow(() -> new IllegalStateException("Unable to resolve the player connection field."));
    return connectionField.get();
  }

  public static CursedComponent curse(String... classNames) {
    Class<?> resolvedClass = classFor(classNames);
    if (resolvedClass == null) {
      throw new IllegalStateException("Unable to resolve any class from " + String.join(", ", classNames));
    }

    return Curse.on(resolvedClass);
  }

  public static Class<?> classFor(String... canonicalNames) {
    for (String canonicalName : canonicalNames) {
      if (canonicalName == null || canonicalName.isBlank()) {
        continue;
      }

      try {
        return Class.forName(canonicalName);
      } catch (ClassNotFoundException ignored) {

      }
    }

    return null;
  }

  public static void sendPacket(Player player, Object packet) {
    if (packet == null) {
      return;
    }

    Class<?> packetClass = classFor(PACKET_CLASS_NAMES);
    if (packetClass == null) {
      throw new IllegalStateException("Unable to resolve the packet interface.");
    }

    Curse.on(getConnection(player)).methodArgs(packetClass).invoke(packet);
  }

  public static int getBlockId(BlockData data) {
    Class<?> blockDataClass = classFor(BLOCK_DATA_CLASS_NAMES);
    if (blockDataClass == null) {
      throw new IllegalStateException("Unable to resolve the block data class.");
    }

    return curse("net.minecraft.world.level.block.Block").fuzzyMethod(FuzzyMethod.builder()
        .returns(int.class)
        .parameter(blockDataClass)
        .build()).orElseThrow(() -> new IllegalStateException("Unable to resolve the block id method.")).invoke(data);
  }

  public static Object removeEntityPacket(int entity) {
    CursedComponent packetClass = curse(REMOVE_ENTITY_PACKET_CLASS_NAMES);

    try {
      return packetClass.construct(new IntArrayList(entity)).instance();
    } catch (Throwable ignored) {

    }

    try {
      return packetClass.construct(entity).instance();
    } catch (Throwable ignored) {

    }

    return packetClass.construct(new int[]{entity}).instance();
  }

  public static Object collectPacket(int entity, int toCollect, int count) {
    Class<?> collectPacketClass = classFor(COLLECT_PACKET_CLASS_NAMES);
    if (collectPacketClass == null) {
      return null;
    }

    return Curse.on(collectPacketClass).construct(entity, toCollect, count).instance();
  }

  public static void sendCollectPacket(Entity at, int radius, int entity, int toCollect, int count) {
    if (collectPacketDisabled) {
      return;
    }

    try {
      Object packet = collectPacket(entity, toCollect, count);
      if (packet == null) {
        disableCollectPacket("collect packet class was not found for this server runtime.");
        return;
      }

      sendPacket(at, radius, packet);
    } catch (Throwable e) {
      String message = e.getMessage();
      disableCollectPacket(e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message));
    }
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

  private static void disableCollectPacket(String reason) {
    collectPacketDisabled = true;
    if (collectPacketWarningIssued) {
      return;
    }

    collectPacketWarningIssued = true;
    React.warn("Mob stacking vacuum packets disabled: " + reason);
  }
}
