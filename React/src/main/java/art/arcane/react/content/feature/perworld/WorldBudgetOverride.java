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

package art.arcane.react.content.feature.perworld;

import art.arcane.react.util.project.config.ConfigDoc;
import lombok.Data;

@Data
public class WorldBudgetOverride {
    @ConfigDoc(value = "Per-world budget threshold in milliseconds overriding the global budgetMs for this world.")
    private double budgetMs;
    @ConfigDoc(value = "Per-world panic threshold in milliseconds overriding the global panicMs for this world.")
    private double panicMs;
    @ConfigDoc(value = "Per-world release threshold in milliseconds overriding the global releaseMs for this world.")
    private double releaseMs;
}
