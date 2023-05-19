package art.arcane.edict.content.parser;

import art.arcane.edict.Edict;
import org.bukkit.entity.Player;

public class TestEdict {
    @Edict.Command("/react message")
    @Edict.Permission("react.message")
    public static void message(@Edict.Contextual @Edict.Required Player player, @Edict.Required String message) {

    }

    @Edict.Command("/react monitor")
    @Edict.Permission("react.monitor")
    @Edict.Aliases({"/react observe", "/react watch"})
    @Edict.PlayerOnly
    public static void monitor(@Edict.Contextual Player player, @Edict.Default("false") @Edict.Aliases("edit") boolean configure) {

    }
}
