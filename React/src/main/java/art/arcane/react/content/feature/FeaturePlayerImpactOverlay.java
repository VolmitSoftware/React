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

import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.content.sampler.SamplerRedstoneUpdates;
import art.arcane.react.util.data.TinyColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class FeaturePlayerImpactOverlay extends FeatureChunkHeatmapBase {
    public static final String ID = "player-impact-overlay";
    private int maxPlayersDrawn = 24;
    private boolean showPlayerInitials = true;

    public FeaturePlayerImpactOverlay() {
        super(ID);
    }

    @Override
    protected String mapLabel() {
        return "Player Impact";
    }

    @Override
    protected TinyColor backgroundColor() {
        return new TinyColor(8, 10, 20);
    }

    @Override
    protected double chunkScore(Chunk chunk) {
        double base = chunkTotalScore(chunk);
        base += chunkSample(chunk, SamplerEntities.ID) * 0.50D;
        base += chunkSample(chunk, SamplerRedstoneUpdates.ID) * 0.30D;
        base += chunkSample(chunk, SamplerHopperUpdates.ID) * 0.20D;
        return base;
    }

    @Override
    protected TinyColor colorFor(double normalized, double rawScore) {
        return gradient(normalized, new TinyColor(20, 45, 120), new TinyColor(90, 180, 255));
    }

    @Override
    protected void renderOverlay(Map<Chunk, Double> score, double min, double max) {
        Player viewer = player();
        if (viewer == null || viewer.getWorld() == null) {
            return;
        }

        Location viewerLocation = viewer.getLocation();
        List<PlayerImpact> impacts = new ArrayList<>();
        for (Player player : viewer.getWorld().getPlayers()) {
            impacts.add(new PlayerImpact(player, playerImpact(player)));
        }

        impacts.sort(Comparator.comparingDouble(PlayerImpact::impact).reversed());
        if (impacts.size() > maxPlayersDrawn) {
            impacts = impacts.subList(0, maxPlayersDrawn);
        }

        double maxImpact = impacts.stream().mapToDouble(PlayerImpact::impact).max().orElse(0D);
        for (PlayerImpact impact : impacts) {
            Location playerLocation = impact.player().getLocation();
            Pixel pixel = projectBlockDelta(viewerLocation, playerLocation);
            int x = pixel.x();
            int y = pixel.y();
            if (x < 0 || y < 0 || x >= width() || y >= height()) {
                continue;
            }

            double normalized = maxImpact <= 0D ? 0D : Math.max(0D, Math.min(1D, impact.impact() / maxImpact));
            TinyColor marker = gradient(normalized, new TinyColor(90, 255, 120), new TinyColor(255, 80, 50));

            set(x, y, marker);
            set(x - 1, y, marker);
            set(x + 1, y, marker);
            set(x, y - 1, marker);
            set(x, y + 1, marker);

            if (impact.player().getUniqueId().equals(viewer.getUniqueId())) {
                set(x, y, new TinyColor(255, 255, 255));
            } else if (showPlayerInitials) {
                String n = impact.player().getName();
                if (!n.isEmpty()) {
                    text(Math.max(0, x + 2), Math.max(0, y - 2), n.substring(0, 1).toUpperCase());
                }
            }
        }
    }

    private double playerImpact(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        double impact = chunkScore(chunk);
        impact += Math.min(3D, player.getVelocity().length()) * 2D;
        if (player.isGliding()) {
            impact += 2D;
        }
        if (player.getVehicle() != null) {
            impact += 1D;
        }
        return impact;
    }

    private record PlayerImpact(Player player, double impact) {
    }
}
