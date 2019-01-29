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
import com.volmit.react.util.C;
import com.volmit.react.util.Ex;
import com.volmit.volume.lang.collections.GList;

public class CommandSubscribe extends ReactCommand
{
	public CommandSubscribe()
	{
		command = Info.COMMAND_SUB;
		aliases = new String[] {Info.COMMAND_SUB_ALIAS_1, Info.COMMAND_SUB_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.MONITOR.getNode()};
		usage = Info.COMMAND_SUB_USAGE;
		description = Info.COMMAND_SUB_DESCRIPTION;
		sideGate = SideGate.PLAYERS_ONLY;

		String ch = "Channels: ";

		for(Note i : Note.values())
		{
			ch += C.WHITE + i.toString().toLowerCase() + ", ";
		}

		registerParameterDescription("[channel]", "The channel to subscribe to. " + ch + C.GRAY);
	}

	@Override
	public List<String> onTabComplete(CommandSender arg0, Command arg1, String arg2, String[] arg3)
	{
		GList<String> l = new GList<String>();
		for(Note i : Note.values())
		{
			if(i.name().toLowerCase().startsWith(arg3[1]))
			{
				l.add(i.name().toLowerCase());
			}
		}
		return l;
	}

	@Override
	public void fire(CommandSender sender, String[] args)
	{
		Player player = (Player) sender;

		if(args.length == 0)
		{
			player.sendMessage(Gate.header("Subscribed", C.AQUA));

			for(Note i : Note.values())
			{
				if(React.instance.messageController.isSubscribed(player, i))
				{
					Gate.msg(player, C.YELLOW + i.toString().toLowerCase());
				}
			}

			player.sendMessage(Gate.header("Subscriptions", C.AQUA));

			for(Note i : Note.values())
			{
				if(!React.instance.messageController.isSubscribed(player, i))
				{
					Gate.msg(player, i.toString().toLowerCase());
				}
			}

			return;
		}

		try
		{
			Note n = Note.valueOf(args[0].toUpperCase());

			if(n != null)
			{
				React.instance.messageController.subscribe(player, Note.valueOf(args[0].toUpperCase()));
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
