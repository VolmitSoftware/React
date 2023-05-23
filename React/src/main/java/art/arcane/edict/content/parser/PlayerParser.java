package art.arcane.edict.content.parser;

import art.arcane.edict.api.context.EdictContextual;
import art.arcane.edict.api.parser.SelectionParser;
import art.arcane.edict.api.parser.Suggestive;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

public class PlayerParser implements SelectionParser<Player>, EdictContextual {
    @Override
    public List<Player> getSelectionOptions() {
        return Bukkit.getOnlinePlayers().stream().map(i -> (Player)i).toList();
    }

    @Override
    public String getName(Player player) {
        return player.getName();
    }
}
