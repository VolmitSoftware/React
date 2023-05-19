package com.volmit.react.content.command;

import com.volmit.react.React;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.format.C;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Decree(
        name = "react",
        aliases = {"re"},
        origin = DecreeOrigin.BOTH,
        description = "The root react command"
)
public class CommandReact implements DecreeExecutor {
    private CommandConfig config;
    private CommandAction action;
    private CommandChunk chunk;
    private CommandEnvironment enviornment;

    @Decree(
        name = "monitor",
        aliases = {"m", "mon"},
        description = "Monitor the server via action bar",
        origin = DecreeOrigin.PLAYER
    )
    public void monitor() {
        React.instance.getPlayerController().getPlayer(player()).toggleActionBar();
        sender().sendMessage(C.REACT + "Action bar monitor " + (React.instance.getPlayerController().getPlayer(player()).isActionBarMonitoring() ? "enabled" : "disabled"));
    }

    public static final List<Material> all = Arrays.stream(Material.values()).filter((m) -> m.getMaxStackSize() == 64 && !m.isLegacy()).collect(Collectors.toList());

    @Decree(
        name = "all",
        description = "Give random items",
        origin = DecreeOrigin.PLAYER
    )
    public void all() {

        for(int i = 0; i < 10; i++)
        {


player().getInventory().addItem(new ItemStack(all.get(RNG.r.i(all.size()-1)), RNG.r.i(1,64)));

        }
    }
}
