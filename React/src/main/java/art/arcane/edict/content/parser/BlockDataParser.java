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

package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;
import art.arcane.edict.api.parser.Suggestive;
import com.volmit.react.util.data.B;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Arrays;
import java.util.List;

public class BlockDataParser implements EdictParser<BlockData>, Suggestive {
    @Override
    public List<String> getOptions() {
        return Arrays.stream(Material.values()).map(i -> i.createBlockData().getAsString(true)).toList();
    }

    @Override
    public boolean isMandatory() {
        return false;
    }

    @Override
    public EdictValue<BlockData> parse(String s) {
        BlockData b = B.get(s);

        if (b.getMaterial() == Material.AIR && !s.toLowerCase().contains("air")) {
            return low(B.get(s));
        }

        return high(b);
    }
}
