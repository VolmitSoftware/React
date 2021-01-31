package com.volmit.react.api;

import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public interface ICommand extends TabCompleter {
    String getCommand();

    String[] getAliases();

    String[] getPermissions();

    String getUsage();

    String getDescription();

    String getDescriptionForParameter(String par);

    SideGate getSideGate();

    void registerParameterDescription(String id, String desc);

    void fire(CommandSender sender, String[] args);
}
