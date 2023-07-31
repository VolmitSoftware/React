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

package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.scheduling.J;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class TweakFastEntityIncineration extends ReactTweak implements Listener {
    public static final String ID = "fast-entity-incineration";
    private double incinerationBeyondNearestPlayer = 32;

    public TweakFastEntityIncineration() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @EventHandler
    public void on(EntityDamageEvent e) {
        if (e.getCause().equals(EntityDamageEvent.DamageCause.FIRE_TICK) && e.getEntity() instanceof Monster m && React.hasNearbyPlayer(m.getLocation(), incinerationBeyondNearestPlayer)) {
            kill(m);
        }
    }

    private void kill(Entity entity) {
        J.s(() -> React.kill(entity, 3), (int) (20 * Math.random()));
    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
