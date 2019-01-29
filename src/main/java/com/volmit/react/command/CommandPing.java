package com.volmit.react.command;

import java.util.Collections;
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
import com.volmit.react.util.Average;
import com.volmit.react.util.C;
import com.volmit.react.util.F;
import com.volmit.react.util.M;
import com.volmit.react.util.P;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public class CommandPing extends ReactCommand
{
	public CommandPing()
	{
		command = Info.COMMAND_PING;
		aliases = new String[] {Info.COMMAND_PING_ALIAS_1, Info.COMMAND_PING_ALIAS_2};
		permissions = new String[] {Permissable.PING.getNode()};
		usage = Info.COMMAND_PING_USAGE;
		description = Info.COMMAND_PING_DESCRIPTION;
		sideGate = SideGate.ANYTHING;
		registerParameterDescription("[player]", "the player to ping (or multiple)");
		registerParameterDescription("[opts]", "-t or --top to view totals");
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
		if(!Capability.ACCELERATED_PING.isCapable())
		{
			Capability.ACCELERATED_PING.sendNotCapable(sender);
			return;
		}

		if(args.length == 0)
		{
			if(sender instanceof Player)
			{
				Gate.msgSuccess(sender, "Your ping is " + C.WHITE + F.f(React.instance.protocolController.ping((Player) sender), 2) + "ms" + C.GRAY + " measured " + F.time(M.ms() - React.instance.protocolController.ago((Player) sender), 0) + " ago");
			}

			else
			{
				Gate.msgError(sender, "The console's ping is probably zero.");
			}

			return;
		}

		if(!Permissable.PING_OTHERS.has(sender))
		{
			Gate.msgError(sender, "You do not have permission to ping other people");
			return;
		}

		if(args.length == 1)
		{
			if(args[0].startsWith("-"))
			{
				if(args[0].equalsIgnoreCase("-t") || args[0].equalsIgnoreCase("--top"))
				{
					GMap<Player, Double> pings = React.instance.protocolController.getPings();
					GList<Double> d = pings.v();
					Collections.sort(d);
					Collections.reverse(d);
					Average a = new Average(d.size());

					for(Double i : d)
					{
						a.put(i);
					}

					double h = d.get(0);
					double l = d.get(d.size() - 1);
					double m = d.get(d.size() / 2);
					Player high = null;
					Player low = null;
					Player mid = null;

					if(pings.isEmpty())
					{
						Gate.msgError(sender, "There is no ping data.");
						return;
					}

					for(Player i : pings.k())
					{
						if(pings.get(i) == h)
						{
							high = i;
						}

						if(pings.get(i) == m)
						{
							mid = i;
						}

						if(pings.get(i) == l)
						{
							low = i;
						}
					}

					sender.sendMessage(Gate.header("Top Ping", C.AQUA));
					Gate.msgSuccess(sender, "Slowest Ping: " + C.RED + F.f(h, 2) + "ms" + C.GRAY + " (" + high.getName() + ")");
					Gate.msgSuccess(sender, "Median Ping: " + C.YELLOW + F.f(m, 2) + "ms" + C.GRAY + " (" + mid.getName() + ")");
					Gate.msgSuccess(sender, "Fastest Ping: " + C.GREEN + F.f(l, 2) + "ms" + C.GRAY + " (" + low.getName() + ")");
					Gate.msgSuccess(sender, "Average Ping: " + C.WHITE + F.f(a.getAverage(), 2));
					sender.sendMessage(Gate.header(C.AQUA));
				}
			}

			else if(P.canFindPlayer(args[0]))
			{
				Player p = P.findPlayer(args[0]);
				Gate.msgSuccess(sender, p.getName() + "'s ping is " + C.WHITE + F.f(React.instance.protocolController.ping(p), 2) + "ms" + C.GRAY + " measured " + F.time(M.ms() - React.instance.protocolController.ago(p), 0) + " ago");
			}

			else
			{
				Gate.msgError(sender, "Who is " + args[0] + "?");
			}
		}
	}
}
