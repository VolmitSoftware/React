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

import art.arcane.react.React;
import art.arcane.react.core.bridge.NmsAccessors;
import art.arcane.react.core.bridge.NmsBridgeRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class NMS {
  private static volatile NmsAccessors accessors;
  private static volatile boolean registryWarningIssued;
  private static volatile boolean collectPacketDisabled;
  private static volatile boolean collectPacketWarningIssued;

  private NMS() {
  }

  public static void reset() {
    synchronized (NMS.class) {
      accessors = null;
      registryWarningIssued = false;
      collectPacketDisabled = false;
      collectPacketWarningIssued = false;
    }
  }

  public static Object getWorldServer(World world) {
    if (world == null) {
      return null;
    }

    NmsAccessors resolved = accessors();
    if (!resolved.worldHandleAvailable()) {
      return null;
    }

    return resolved.worldHandle(world);
  }

  public static Object getHandle(Entity entity) {
    return accessors().entityHandle(entity);
  }

  public static Object getConnection(Player player) {
    return accessors().connection(getHandle(player));
  }

  public static void sendPacket(Player player, Object packet) {
    if (packet == null) {
      return;
    }

    accessors().send(getConnection(player), packet);
  }

  public static Object collectPacket(int entity, int toCollect, int count) {
    NmsAccessors resolved = accessors();
    if (!resolved.collectPacketAvailable()) {
      return null;
    }

    return resolved.collectPacket(entity, toCollect, count);
  }

  public static void sendCollectPacket(Entity at, int radius, int entity, int toCollect, int count) {
    if (collectPacketDisabled) {
      return;
    }

    try {
      Object packet = collectPacket(entity, toCollect, count);
      if (packet == null) {
        disableCollectPacket("collect packet class was not found for this server runtime.", null);
        return;
      }

      sendPacket(at, radius, packet);
    } catch (Throwable e) {
      String message = e.getMessage();
      disableCollectPacket(e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message), e);
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

  private static NmsAccessors accessors() {
    NmsAccessors resolved = accessors;
    if (resolved != null) {
      return resolved;
    }

    synchronized (NMS.class) {
      if (accessors != null) {
        return accessors;
      }

      NmsBridgeRegistry registry = React.instance == null ? null : React.bridgeRegistry();
      if (registry == null) {
        warnRegistryUnavailable();
        return NmsAccessors.unresolved();
      }

      accessors = NmsAccessors.resolve(registry, NmsAccessors.defaultDescriptors(), React::warn);
      return accessors;
    }
  }

  private static void warnRegistryUnavailable() {
    if (registryWarningIssued) {
      return;
    }

    registryWarningIssued = true;
    React.warn("NMS accessors are inactive: the React bridge registry has not been initialised.");
  }

  private static void disableCollectPacket(String reason, Throwable cause) {
    collectPacketDisabled = true;
    if (collectPacketWarningIssued) {
      return;
    }

    collectPacketWarningIssued = true;
    React.warn("Mob stacking vacuum packets disabled: " + reason);
    if (cause != null) {
      React.reportError(cause);
    }
  }
}
