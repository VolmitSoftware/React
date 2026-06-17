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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ReactRenderContext {
  private static Map<Long, ReactRenderContext> contexts = new ConcurrentHashMap<>();
  private Player player;
  private MapView view;
  private MapCanvas canvas;
  private int width;
  private int height;
  @Builder.Default
  private int offsetX = 0;
  @Builder.Default
  private int offsetY = 0;
  @Builder.Default
  private int scaleX = 1;
  @Builder.Default
  private int scaleY = 1;
  @Builder.Default
  private int textScale = 1;

  public static void push(ReactRenderContext context) {
    contexts.put(Thread.currentThread().getId(), context);
  }

  public static void pop() {
    contexts.remove(Thread.currentThread().getId());
  }

  public static ReactRenderContext of() {
    return contexts.get(Thread.currentThread().getId());
  }
}
