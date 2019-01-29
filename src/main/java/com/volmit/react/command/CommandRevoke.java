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
import com.volmit.react.util.P;
import com.volmit.volume.lang.collections.GList;

public class CommandRevoke extends ReactCommand
{
	public CommandRevoke()
	{
		command = Info.COMMAND_REVOKE;
		aliases = new String[] {Info.COMMAND_REVOKE_ALIAS_1, Info.COMMAND_REVOKE_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.toString()};
		usage = Info.COMMAND_REVOKE_USAGE;
		description = Info.COMMAND_REVOKE_DESCRIPTION;
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

		if(sender instanceof Player && Permissable.isAccessor((Player) sender))
		{
			if(args.length == 1 && P.findPlayer(args[0]) != null && P.findPlayer(args[0]).equals(sender))
			{
				Permissable.removeAccesssor((Player) sender);
				Gate.msgSuccess(sender, "Access self revoked. Have a nice day.");
				return;
			}

			Gate.msgError(sender, "Creative, but sorry. You need real access to do this :P");
			return;
		}

		if(args.length == 1)
		{
			Player p = P.findPlayer(args[0]);

			if(p != null)
			{
				if(!Permissable.isAccessor(p))
				{
					Gate.msgError(sender, p.getName() + " does not have temporary access. You can add with /re accept <player>");
					return;
				}

				Permissable.removeAccesssor(p);
				Gate.msgSuccess(sender, p.getName() + "'s react privileges revoked.");
			}

			else
			{
				Gate.msgError(sender, "Cant find '" + args[0] + "'. Make sure your keyboard is plugged in.");
			}
		}

		else
		{
			Gate.msgError(sender, "/re revoke <PLAYER>");
		}
	}
}
