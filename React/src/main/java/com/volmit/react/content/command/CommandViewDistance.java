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

package com.volmit.react.content.command;

import art.arcane.curse.Curse;
import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;

public class CommandViewDistance {

    @Edict.PlayerOnly
    @Edict.Command("/react set-player-view-distance")
    @Edict.Aliases({"/re vd", "/re view-distance"})
    public static void vd(
            @Edict.Default("32")
            @Edict.Aliases({"distance", "d"})
            int d
    ) {
        if (d > 32)
            d = 32;

        Curse.on(EdictContext.player().getWorld()).method("setViewDistance", int.class).invoke(d);
        Curse.on(EdictContext.player().getWorld()).method("setSimulationDistance", int.class).invoke(d);
    }

}
