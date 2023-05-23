package art.arcane.edict.content.parser;

import art.arcane.edict.api.context.EdictContextual;
import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;
import art.arcane.edict.api.parser.SelectionParser;
import art.arcane.edict.api.parser.Suggestive;
import com.volmit.react.util.data.B;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

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

        if(b.getMaterial() == Material.AIR && !s.toLowerCase().contains("air")) {
            return low(B.get(s));
        }

        return high(b);
    }
}
