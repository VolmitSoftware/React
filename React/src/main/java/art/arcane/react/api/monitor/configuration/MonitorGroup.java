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

package art.arcane.react.api.monitor.configuration;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerUnknown;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.awt.*;
import java.util.ArrayList;
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
    Sampler sampler = React.sampler(getHeadOrSomething());
    if (sampler != null) {
      return sampler;
    }

    if (samplers != null) {
      for (String samplerId : new ArrayList<>(samplers)) {
        Sampler candidate = React.sampler(samplerId);
        if (candidate != null) {
          head = samplerId;
          return candidate;
        }
      }
    }

    return React.sampler(SamplerUnknown.ID);
  }

  public void setHeadSampler(String s) {
    head = s;
  }

  public List<Sampler> getSubSamplers() {
    if (samplers == null) {
      return List.of();
    }
    return samplers.stream().skip(1).map(i -> (Sampler) React.sampler(i)).filter(i -> i != null).collect(Collectors.toList());
  }

  public int getColorValue() {
    return Color.decode(color).getRGB();
  }
}
