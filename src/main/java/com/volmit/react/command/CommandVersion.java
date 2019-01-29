package com.volmit.react.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.ReactPlugin;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.C;
import com.volmit.volume.lang.collections.GList;

public class CommandVersion extends ReactCommand
{
	public CommandVersion()
	{
		command = Info.COMMAND_VERSION;
		aliases = new String[] {Info.COMMAND_VERSION_ALIAS_1, Info.COMMAND_VERSION_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode()};
		usage = Info.COMMAND_VERSION_USAGE;
		description = Info.COMMAND_VERSION_DESCRIPTION;
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
		String vt = "";
		String vs = ReactPlugin.i.getDescription().getVersion();

		if(vs.contains("a"))
		{
			vt += C.RED + C.UNDERLINE.toString() + vs;
		}

		else
		{
			vt += C.GREEN + vs;
		}

		Gate.msgSuccess(sender, C.WHITE + "React " + vt);
	}
}
