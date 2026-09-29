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

package art.arcane.react.api.sampler;

import art.arcane.react.React;
import art.arcane.react.core.controller.EventController;

public abstract class ReactEventSampler extends ReactCachedSampler {
  private transient volatile EventController eventController;
  private transient volatile boolean sampledWhileMeasuring;

  protected ReactEventSampler(String id, long sampleDelay) {
    super(id, sampleDelay);
  }

  protected abstract double onMeasuredSample(EventController controller);

  @Override
  public final double onSample() {
    EventController controller = controller();
    boolean measuring = controller != null && controller.isMeasuring();
    sampledWhileMeasuring = measuring;
    return measuring ? onMeasuredSample(controller) : 0D;
  }

  @Override
  public boolean isSampleAvailable() {
    if (!sampledWhileMeasuring) {
      return false;
    }

    EventController controller = controller();
    return controller != null && controller.isMeasuring();
  }

  @Override
  public void markDemand() {
    EventController controller = controller();
    if (controller != null) {
      controller.markSamplerActivity();
    }
  }

  @Override
  public void start() {
    super.start();
    sampledWhileMeasuring = false;
    eventController = React.controller(EventController.class);
  }

  @Override
  public void stop() {
    super.stop();
    eventController = null;
  }

  private EventController controller() {
    EventController controller = eventController;
    if (controller == null) {
      controller = React.controller(EventController.class);
      eventController = controller;
    }
    return controller;
  }
}
