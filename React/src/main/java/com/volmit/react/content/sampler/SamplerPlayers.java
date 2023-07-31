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

package com.volmit.react.content.sampler;

import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Listener;

public class SamplerPlayers extends ReactCachedSampler implements Listener {
    public static final String ID = "players";

    public SamplerPlayers() {
        super(ID, 250);
    }

    @Override
    public Material getIcon() {
        return Material.TOTEM_OF_UNDYING;
    }

    @Override
    public double onSample() {
        return Bukkit.getServer().getOnlinePlayers().size();
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "PLR";
    }
}
