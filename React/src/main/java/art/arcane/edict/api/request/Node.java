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

package art.arcane.edict.api.request;

import art.arcane.edict.Edict;
import lombok.Getter;

import java.util.List;

public class Node {
    @Getter
    private List<String> aliases;

    public Node(List<String> aliases) {
        this.aliases = aliases;
    }

    public boolean matches(String from) {
        return aliases.stream().anyMatch(i -> i.equalsIgnoreCase(from));
    }

    public boolean matches(Node from) {
        return aliases.stream().anyMatch(i -> from.getAliases().stream().anyMatch(i::equalsIgnoreCase));
    }

    public int getDistance(String from) {
        return matches(from) ? 0 : aliases.stream().mapToInt((e) -> Edict.calculateLevenshteinDistance(from, e)).min().orElse(0);
    }

    @Override
    public String toString() {
        return aliases.get(0);
    }
}
