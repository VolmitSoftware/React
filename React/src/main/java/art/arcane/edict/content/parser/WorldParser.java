package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.SelectionParser;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;

public class WorldParser implements SelectionParser<World> {
    @Override
    public List<World> getSelectionOptions() {
        return Bukkit.getWorlds().stream().toList();
    }

    @Override
    public String getName(World world) {
        return world.getName();
    }
}
