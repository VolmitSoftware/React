package com.volmit.react.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.React;
import com.volmit.react.api.Capability;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.volume.lang.collections.GList;

public class CommandMonitor extends ReactCommand
{
	public CommandMonitor()
	{
		command = Info.COMMAND_MONITOR;
		aliases = new String[] {Info.COMMAND_MONITOR_ALIAS_1, Info.COMMAND_MONITOR_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.MONITOR_TITLE.getNode()};
		usage = Info.COMMAND_MONITOR_USAGE;
		description = Info.COMMAND_MONITOR_DESCRIPTION;
		sideGate = SideGate.PLAYERS_ONLY;
		registerParameterDescription("(toggle)", "Using this command either turns it on or off.");
		registerParameterDescription("[options]", "-- high or -h to push the monitor up or down (toggle)");
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

		if(!Capability.TITLE_BAR.isCapable())
		{
			Capability.TITLE_BAR.sendNotCapable(player);
			return;
		}

		if(!Capability.ACTION_BAR.isCapable())
		{
			Capability.ACTION_BAR.sendNotCapable(player);
			return;
		}

		if(args.length > 0 && (args[0].equalsIgnoreCase("-h") || args[0].equalsIgnoreCase("--high")))
		{
			Player p = (Player) sender;
			React.instance.playerController.getPlayer(p).highMonitor = !React.instance.playerController.getPlayer(p).highMonitor;
			Gate.msgSuccess(p, "High Monitor " + (React.instance.playerController.getPlayer(p).highMonitor ? "enabled" : "disabled"));
			return;
		}

		if(args.length > 0 && (args[0].equalsIgnoreCase("-l") || args[0].equalsIgnoreCase("--lock")))
		{
			Player p = (Player) sender;
			React.instance.monitorController.doLock(p);
			return;
		}

		React.instance.monitorController.toggleMonitoring(player);
	}
}
