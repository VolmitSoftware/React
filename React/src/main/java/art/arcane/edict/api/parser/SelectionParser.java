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

package art.arcane.edict.api.parser;

import art.arcane.edict.Edict;

import java.util.Comparator;
import java.util.List;

public interface SelectionParser<T> extends EdictParser<T>, Suggestive {
    private static int calculateSimilarity(String enumConstantName, String targetString) {
        return Edict.calculateLevenshteinDistance(enumConstantName, targetString);
    }

    default EdictValue<T> parse(String s) {
        return getSelectionOptions().stream()
                .min(Comparator.comparingInt(enumConstant -> calculateSimilarity(getName(enumConstant), s.trim())))
                .map(this::high)
                .orElse(low(getDefault()));
    }

    List<T> getSelectionOptions();

    default T getDefault() {
        return getSelectionOptions().get(0);
    }

    String getName(T t);

    default List<String> getOptions() {
        return getSelectionOptions().stream()
                .map(this::getName)
                .toList();
    }

    default boolean isMandatory() {
        return true;
    }
}
