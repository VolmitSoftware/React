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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MegamapDuplicateSplitter {
  private MegamapDuplicateSplitter() {
  }

  public record DuplicateFrame(UUID frameId, int mapId, String rendererId, long firstSeenMs) {
  }

  public static Set<UUID> plan(Collection<DuplicateFrame> frames) {
    if (frames == null || frames.isEmpty()) {
      return Set.of();
    }

    Map<Integer, List<DuplicateFrame>> framesByMapId = new HashMap<>();
    for (DuplicateFrame frame : frames) {
      if (frame == null || frame.frameId() == null || frame.mapId() < 0) {
        continue;
      }

      String rendererId = frame.rendererId();
      if (rendererId == null || rendererId.isBlank()) {
        continue;
      }

      framesByMapId.computeIfAbsent(frame.mapId(), ignored -> new ArrayList<>()).add(frame);
    }

    Set<UUID> reassign = new LinkedHashSet<>();
    for (List<DuplicateFrame> group : framesByMapId.values()) {
      if (group.size() < 2) {
        continue;
      }

      // The oldest frame keeps the shared map id so the picture a player already
      // placed stays where it is; every later copy gets a fresh id instead.
      DuplicateFrame keep = group.get(0);
      for (int i = 1; i < group.size(); i++) {
        if (outranks(group.get(i), keep)) {
          keep = group.get(i);
        }
      }

      for (DuplicateFrame frame : group) {
        if (!frame.frameId().equals(keep.frameId())) {
          reassign.add(frame.frameId());
        }
      }
    }

    return Set.copyOf(reassign);
  }

  private static boolean outranks(DuplicateFrame candidate, DuplicateFrame incumbent) {
    if (candidate.firstSeenMs() != incumbent.firstSeenMs()) {
      return candidate.firstSeenMs() < incumbent.firstSeenMs();
    }

    return candidate.frameId().compareTo(incumbent.frameId()) < 0;
  }
}
