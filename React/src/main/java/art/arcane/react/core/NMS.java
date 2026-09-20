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
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class NMS {
    private static volatile boolean collectPacketDisabled;
    private static volatile NativeWorldAccess nativeAccess;

    private NMS() {
    }

    public static void reset() {
        collectPacketDisabled = false;
        nativeAccess = null;
    }

    public static void sendCollectPacket(Entity at, int radius, int entity, int toCollect, int count) {
        if (collectPacketDisabled) {
            return;
        }
        try {
            NativeWorldAccess access = nativeAccess;
            if (access == null) {
                access = NativeAdapters.find(NativeWorldAccess.class).orElse(null);
                nativeAccess = access;
            }
            if (access == null) {
                collectPacketDisabled = true;
                React.warn("Mob stacking vacuum packets disabled: native world access is unavailable.");
                return;
            }
            Location location = at.getLocation();
            for (Player player : location.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(location) < (double) radius * radius) {
                    access.sendCollectPacket(player, entity, toCollect, count);
                }
            }
        } catch (Throwable failure) {
            collectPacketDisabled = true;
            React.reportError("Mob stacking vacuum packets disabled", failure);
        }
    }
}
