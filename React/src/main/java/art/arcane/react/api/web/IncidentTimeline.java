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

package art.arcane.react.api.web;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class IncidentTimeline {
  private static volatile IncidentTimeline GLOBAL;

  private final int capacity;
  private final ArrayDeque<String> ring;

  public IncidentTimeline(int capacity) {
    this.capacity = capacity;
    this.ring = new ArrayDeque<>(capacity);
  }

  public static IncidentTimeline global() {
    if (GLOBAL == null) {
      synchronized (IncidentTimeline.class) {
        if (GLOBAL == null) {
          GLOBAL = new IncidentTimeline(64);
        }
      }
    }
    return GLOBAL;
  }

  public synchronized void record(String line) {
    String entry = "[" + Instant.now().toEpochMilli() + "] " + line;
    ring.addLast(entry);
    while (ring.size() > capacity) {
      ring.removeFirst();
    }
  }

  public synchronized List<String> recent(int n) {
    if (n <= 0) {
      return new ArrayList<>(0);
    }
    int size = ring.size();
    int skip = Math.max(0, size - n);
    List<String> result = new ArrayList<>(Math.min(n, size));
    int index = 0;
    for (String entry : ring) {
      if (index >= skip) {
        result.add(entry);
      }
      index++;
    }
    return result;
  }

  public synchronized int size() {
    return ring.size();
  }
}
