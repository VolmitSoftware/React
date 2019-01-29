package com.volmit.react.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.api.TemporaryAccessor;
import com.volmit.react.util.C;
import com.volmit.volume.lang.collections.GList;

public class CommandRequests extends ReactCommand
{
	public CommandRequests()
	{
		command = Info.COMMAND_REQUESTS;
		aliases = new String[] {Info.COMMAND_REQUESTS_ALIAS_1, Info.COMMAND_REQUESTS_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.toString()};
		usage = Info.COMMAND_REQUESTS_USAGE;
		description = Info.COMMAND_REQUESTS_DESCRIPTION;
		sideGate = SideGate.ANYTHING;
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
		if(!Config.ALLOW_TEMPACCESS)
		{
			Gate.msgError(sender, "Temporary Access is disabled.");
			return;
		}

		sender.sendMessage(Gate.header("Accessors", C.AQUA));

		for(Player i : CommandAccess.req)
		{
			sender.sendMessage(C.YELLOW + "REQUEST: " + C.WHITE + i.getName());
		}

		for(TemporaryAccessor i : Permissable.accessors)
		{
			sender.sendMessage(C.RED + "HAS ACCESS: " + C.WHITE + i.getPlayer().getName());
		}

		sender.sendMessage(Gate.header(C.AQUA));
	}
}
