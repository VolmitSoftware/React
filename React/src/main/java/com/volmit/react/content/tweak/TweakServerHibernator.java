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
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public class TweakServerHibernator extends ReactTweak implements Listener {
    public static final String ID = "server-hibernator";
    private transient boolean firstRun = true;
    private double secondsPerTick = 1.0;


    public TweakServerHibernator() {
        super(ID);
    }

    @Override
    public void onActivate() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(React.instance, () -> {
            if (Bukkit.getOnlinePlayers().size() == 0) {
                if (this.firstRun) {
                    React.info("React Engaged Catatonic Sleep.");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
                }
                try {
                    Thread.sleep((long) (1000 * secondsPerTick));
                    this.firstRun = false;
                } catch (Exception ignored) {
                }
            } else {
                if (!firstRun) {
                    React.info("React Disengaged Catatonic Sleep, Waking Up.");
                }
                this.firstRun = true;
            }

        }, 0L, 1L);

    }

    @Override
    public void onDeactivate() {
        Bukkit.getScheduler().cancelTasks(React.instance);

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {
    }
}
