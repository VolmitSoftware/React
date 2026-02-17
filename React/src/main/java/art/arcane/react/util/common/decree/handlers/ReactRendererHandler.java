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

package art.arcane.react.util.decree.handlers;

import art.arcane.react.React;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.MapController;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.react.util.decree.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class ReactRendererHandler implements DirectorParameterHandler<ReactRenderer> {
    @Override
    public KList<ReactRenderer> getPossibilities() {
        KList<ReactRenderer> options = new KList<>();
        MapController controller = React.controller(MapController.class);
        if (controller == null || controller.getRenderers() == null) {
            return options;
        }

        options.addAll(controller.getRenderers().values());
        return options;
    }

    @Override
    public String toString(ReactRenderer world) {
        return world.getId();
    }

    @Override
    public ReactRenderer parse(String in, boolean force) throws DirectorParsingException {
        KList<ReactRenderer> options = getPossibilities(in);

        if (options.isEmpty()) {
            throw new DirectorParsingException("Unable to find Renderer \"" + in + "\"");
        }
        try {
            return options.stream().filter((i) -> toString(i).equalsIgnoreCase(in)).toList().get(0);
        } catch (Throwable e) {
            throw new DirectorParsingException("Unable to filter which Renderer \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(ReactRenderer.class);
    }

    @Override
    public String getRandomDefault() {
        return "unknown";
    }
}
