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

import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Player Impact Overlay feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeaturePlayerImpactOverlay extends FeatureChunkHeatmapBase implements PlayerImpactHeatmap.PlayerSettings {
  public static final String ID = PlayerImpactHeatmap.ID;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum players drawn allowed by player impact overlay.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxPlayersDrawn = 24;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether player impact overlay renders show player initials on map output.", impact = "Enable to show this visual layer; disable for a cleaner map and slightly lower render cost.")
  private boolean showPlayerInitials = true;

  public FeaturePlayerImpactOverlay() {
    super(ID);
  }

  @Override
  protected ChunkHeatmapRenderer createRenderer(ChunkHeatmapHost data, MapAnchorHost anchors) {
    return new PlayerImpactHeatmap(data, anchors, this);
  }

  @Override
  public int maxPlayersDrawn() {
    return maxPlayersDrawn;
  }

  @Override
  public boolean showPlayerInitials() {
    return showPlayerInitials;
  }
}
