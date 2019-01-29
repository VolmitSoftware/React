package com.volmit.react.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.React;
import com.volmit.react.api.IFix;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.C;
import com.volmit.volume.lang.collections.GList;

public class CommandFix extends ReactCommand
{
	public CommandFix()
	{
		command = Info.COMMAND_FIX;
		aliases = new String[] {Info.COMMAND_FIX_ALIAS_1, Info.COMMAND_FIX_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode()};
		usage = Info.COMMAND_FIX_USAGE;
		description = Info.COMMAND_FIX_DESCRIPTION;
		sideGate = SideGate.ANYTHING;
	}

	@Override
	public List<String> onTabComplete(CommandSender arg0, Command arg1, String arg2, String[] arg3)
	{
		GList<String> l = new GList<String>();

		for(IFix i : React.instance.fixController.getFixes())
		{
			if(i.getId().toLowerCase().startsWith(arg3[1]))
			{
				l.add(i.getId());
			}
		}

		for(IFix i : React.instance.fixController.getFixes())
		{
			for(String j : i.getAliases())
			{
				if(j.toLowerCase().startsWith(arg3[1]))
				{
					l.add(j);
				}
			}
		}

		return l;
	}

	@Override
	public void fire(CommandSender sender, String[] args)
	{
		if(args.length == 0)
		{
			sender.sendMessage(Gate.header("Fixes", C.AQUA));

			for(IFix i : React.instance.fixController.getFixes())
			{
				sender.sendMessage(C.AQUA + "/re fix " + C.GRAY + i.getId() + " " + C.WHITE + i.getUsage());
			}

			sender.sendMessage(Gate.header(C.AQUA));
			return;
		}

		String name = args[0];
		GList<String> argx = new GList<String>(args);
		argx.remove(0);
		React.instance.fixController.runFix(sender, name, argx.toArray(new String[argx.size()]));
	}
}
