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

package com.volmit.react.model;

import com.google.gson.Gson;
import com.volmit.react.React;
import com.volmit.react.api.monitor.configuration.MonitorConfiguration;
import com.volmit.react.util.io.IO;
import com.volmit.react.util.json.JSONObject;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Data
public class PlayerSettings {
    private MonitorConfiguration monitorConfiguration;
    private boolean actionBarMonitoring = false;
    private boolean visualizing = false;

    public static void saveSettings(UUID player, PlayerSettings s) {
        File l = React.instance.getDataFile("player-settings", player.toString() + ".json");

        try {
            IO.writeAll(l, new JSONObject(new Gson().toJson(s)).toString(4));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static PlayerSettings get(UUID player) {
        PlayerSettings dummy = new PlayerSettings();
        PlayerSettings configuration = null;
        File l = React.instance.getDataFile("player-settings", player.toString() + ".json");

        if (!l.exists()) {
            try {
                IO.writeAll(l, new JSONObject(new Gson().toJson(dummy)).toString(4));
            } catch (IOException e) {
                e.printStackTrace();
                configuration = dummy;
                return dummy;
            }
        }

        try {
            configuration = new Gson().fromJson(IO.readAll(l), PlayerSettings.class);
            IO.writeAll(l, new JSONObject(new Gson().toJson(configuration)).toString(4));
        } catch (IOException e) {
            e.printStackTrace();
            configuration = new PlayerSettings();
        }

        return configuration;
    }

    public void toggleVisualizing() {
        visualizing = !visualizing;
    }

    public boolean isVisualizing() {
        return visualizing;
    }

    public void init() {
        if (monitorConfiguration == null) {
            monitorConfiguration = new Gson().fromJson(new Gson().toJson(ReactConfiguration.get().getMonitoring().getMonitorConfiguration()), MonitorConfiguration.class);
        }
    }
}
