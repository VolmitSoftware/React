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

package art.arcane.react.api.rendering;

import art.arcane.react.React;
import art.arcane.react.content.feature.FeatureUnknown;
import art.arcane.react.core.controller.MapController;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

@Getter
@AllArgsConstructor
public class MapRendererPipe extends MapRenderer {
    private volatile ReactRenderer renderer;
    private final String rendererId;

    public MapRendererPipe(ReactRenderer renderer) {
        this.renderer = renderer;
        this.rendererId = renderer == null ? FeatureUnknown.ID : renderer.getId();
    }

    private ReactRenderer resolveRenderer() {
        try {
            MapController controller = React.controller(MapController.class);
            if (controller != null) {
                ReactRenderer resolved = controller.getRendererById(rendererId);
                if (resolved != null) {
                    renderer = resolved;
                    return resolved;
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the last known renderer reference.
        }

        if (renderer == null) {
            renderer = new RendererUnknown();
        }

        return renderer;
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        try {
            MapController controller = React.controller(MapController.class);
            if (controller != null && !controller.shouldRenderForPlayer(map, player)) {
                return;
            }

            ReactRenderContext.push(ReactRenderContext.builder()
                    .player(player)
                    .view(map)
                    .canvas(canvas)
                    .width(128)
                    .height(128)
                    .build());
            resolveRenderer().render(map, canvas, player);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
