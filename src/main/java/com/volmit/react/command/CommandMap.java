package com.volmit.react.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Info;
import com.volmit.react.React;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.volume.lang.collections.GList;

public class CommandMap extends ReactCommand
{
	public CommandMap()
	{
		command = Info.COMMAND_MAP;
		aliases = new String[] {Info.COMMAND_MAP_ALIAS_1, Info.COMMAND_MAP_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.MONITOR_MAP.getNode()};
		usage = Info.COMMAND_MAP_USAGE;
		description = Info.COMMAND_MAP_DESCRIPTION;
		sideGate = SideGate.PLAYERS_ONLY;
		registerParameterDescription("(toggle)", "Using this command either turns it on or off.");
		registerParameterDescription("[options]", "-i To place map on item frame\n-e to view eod map view");
	}

	@Override
	public List<String> onTabComplete(CommandSender arg0, Command arg1, String arg2, String[] arg3)
	{
		GList<String> l = new GList<String>();

		return l;
	}

	@Override
	public void fire(CommandSender sender, String[] args)
	{
		Player player = (Player) sender;

		React.instance.graphController.toggleMapping(player, args);
	}
}
