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

package com.volmit.react.api.monitor.configuration;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.content.sampler.SamplerUnknown;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Data
public class MonitorGroup {
    private String color;
    private String name;

    @Singular
    private List<String> samplers;
    private String head;

    public String getHeadOrSomething() {
        if (head == null) {
            if (samplers.size() > 0) {
                head = samplers.get(0);
            } else {
                head = SamplerUnknown.ID;
            }
        }

        return head;
    }

    public Sampler getHeadSampler() {
        return React.sampler(getHeadOrSomething());
    }

    public void setHeadSampler(String s) {
        head = s;
    }

    public List<Sampler> getSubSamplers() {
        return samplers.stream().skip(1).map(i -> (Sampler) React.sampler(i)).collect(Collectors.toList());
    }

    public int getColorValue() {
        return Color.decode(color).getRGB();
    }
}
