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

package com.volmit.react.content.command.parser;

import art.arcane.edict.api.parser.SelectionParser;
import com.volmit.react.React;
import com.volmit.react.api.rendering.ReactRenderer;
import com.volmit.react.core.controller.MapController;

import java.util.List;

public class RendererParser implements SelectionParser<ReactRenderer> {
    @Override
    public List<ReactRenderer> getSelectionOptions() {
        return React.controller(MapController.class).getRenderers().values().stream().toList();
    }

    @Override
    public String getName(ReactRenderer renderer) {
        return renderer.getId();
    }
}
