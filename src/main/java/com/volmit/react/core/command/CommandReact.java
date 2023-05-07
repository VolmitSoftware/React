package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.api.command.RCommand;
import com.volmit.react.api.command.RConst;
import dev.jorel.commandapi.annotations.Alias;
import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Default;
import dev.jorel.commandapi.annotations.Permission;
import org.bukkit.command.CommandSender;

@Command("react")
@Alias({"re", "ract"})
@Permission("react.main")
public class CommandReact implements RCommand {
    @Default
    public static void react(CommandSender sender) {
        RConst.success("React v" + React.instance.getDescription().getVersion() + " by Volmit Software");
        RConst.success("Type /react help for a list of commands");
    }
}
