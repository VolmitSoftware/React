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

package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

public class SamplerProcessorOutsideLoad extends ReactCachedSampler {
    public static final String ID = "processor-outside";

    public SamplerProcessorOutsideLoad() {
        super(ID, 250);
    }

    @Override
    public Material getIcon() {
        return Material.GREEN_CANDLE;
    }

    @Override
    public double onSample() {
        double systemLoad = normalizeCpuLoad(getSampler(SamplerProcessorSystemLoad.ID).sample());
        double processLoad = normalizeCpuLoad(getSampler(SamplerProcessorProcessLoad.ID).sample());
        return Math.max(0D, systemLoad - processLoad);
    }

    @Override
    public String formattedValue(double t) {
        return Form.pc(normalizeCpuLoad(t), 0);
    }

    @Override
    public String formattedSuffix(double t) {
        return "xCPU";
    }

    private static double normalizeCpuLoad(double raw) {
        if (!Double.isFinite(raw) || raw <= 0D) {
            return 0D;
        }

        if (raw <= 1D) {
            return raw;
        }

        if (raw <= 100D) {
            return raw / 100D;
        }

        return 1D;
    }
}
