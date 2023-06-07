package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.SelectionParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class PlayerParser implements SelectionParser<Player> {
    @Override
    public List<Player> getSelectionOptions() {
        return Bukkit.getOnlinePlayers().stream().map(i -> (Player) i).toList();
    }

    @Override
    public String getName(Player player) {
        return player.getName();
    }
}
