package com.volmit.react.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.React;
import com.volmit.react.api.Note;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.Ex;
import com.volmit.volume.lang.collections.GList;

public class CommandUnsubscribe extends ReactCommand
{
	public CommandUnsubscribe()
	{
		command = Info.COMMAND_USUB;
		aliases = new String[] {Info.COMMAND_USUB_ALIAS_1, Info.COMMAND_USUB_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.MONITOR.getNode()};
		usage = Info.COMMAND_USUB_USAGE;
		description = Info.COMMAND_USUB_DESCRIPTION;
		sideGate = SideGate.PLAYERS_ONLY;
		registerParameterDescription("[channel]", "The channel to unsubscribe from");
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

		if(args.length == 0)
		{
			for(Note i : Note.values())
			{
				Gate.msg(player, i.toString().toLowerCase());
			}

			return;
		}

		try
		{
			Note n = Note.valueOf(args[0].toUpperCase());

			if(n != null)
			{
				React.instance.messageController.unsubscribe(player, Note.valueOf(args[0].toUpperCase()));
			}

			else
			{
				Gate.msgError(player, "Unknown channel");
			}
		}

		catch(Throwable e)
		{
			Ex.t(e);
			Gate.msgError(player, "Unknown channel");
		}
	}
}
