package test;

import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;
import com.volmit.react.React;
import org.bukkit.entity.Player;

public class TestEdict {
    @Edict.Command("/react monitor")
    public static void monitor(Player player) {
        React.instance.monitorController.toggle(player);
    }

    @Edict.Command("/react monitor")
    public static void monitor(Player player, boolean configure) {
        React.instance.monitorController.configure(player);
    }

    @Edict.Command("/react monitor")
    public static void monitor(boolean configure) {
        React.instance.monitorController.configure(EdictContext.get().getPlayer());
    }

    @Edict.Command("/react monitor")
    public static void monitor() {
        React.instance.monitorController.toggle(EdictContext.get().getPlayer());
    }
}
