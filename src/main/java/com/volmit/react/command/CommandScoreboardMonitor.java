package com.volmit.react.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Info;
import com.volmit.react.React;
import com.volmit.react.api.Capability;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.volume.lang.collections.GList;

public class CommandScoreboardMonitor extends ReactCommand
{
	public CommandScoreboardMonitor()
	{
		command = Info.COMMAND_SCOREBOARDLOG;
		aliases = new String[] {Info.COMMAND_ACTIONLOG_ALIAS_1, Info.COMMAND_ACTIONLOG_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.MONITOR_TITLE.getNode()};
		usage = Info.COMMAND_ACTIONLOG_USAGE;
		description = Info.COMMAND_ACTIONLOG_DESCRIPTION;
		sideGate = SideGate.PLAYERS_ONLY;
		registerParameterDescription("(toggle)", "Using this command either turns it on or off.");
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

		if(!Capability.SCOREBOARD.isCapable())
		{
			Capability.SCOREBOARD.sendNotCapable(player);
			return;
		}

		React.instance.monitorController.toggleActionLog(player);
	}
}
