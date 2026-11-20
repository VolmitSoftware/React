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

package art.arcane.react.content.feature;

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Plugin Event Impact List Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeaturePluginEventImpactListMap extends ReactFeature implements ReactRenderer {
  public static final String ID = OwnerEventImpactListRenderer.ID;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum rows drawn per map tile, or 0 to fill the available height.", impact = "Zero lets a taller map wall list every plugin; a fixed value multiplies by the number of vertical tiles, so a taller wall still lists more plugins.")
  private int maxRowsPerTile = 0;
  private final transient OwnerEventImpactListRenderer renderer = new OwnerEventImpactListRenderer(
      MapFeatureHosts.anchors(),
      MapFeatureHosts::events,
      () -> maxRowsPerTile
  );

  public FeaturePluginEventImpactListMap() {
    super(ID);
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return renderer.megamapCapability();
  }

  @Override
  public void render() {
    renderer.render();
  }
}
