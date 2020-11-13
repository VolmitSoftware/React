package com.volmit.react.command;

import java.util.Collections;
import java.util.List;

import com.volmit.react.*;
import com.volmit.react.util.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.api.Capability;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;

import primal.lang.collection.GList;
import primal.lang.collection.GMap;
import primal.util.text.C;

public class CommandPing extends ReactCommand
{
	public CommandPing()
	{
		command = Info.COMMAND_PING;
		aliases = new String[] {Info.COMMAND_PING_ALIAS_1, Info.COMMAND_PING_ALIAS_2, Info.COMMAND_PING_ALIAS_3 };
		permissions = new String[] {Permissable.PING.getNode()};
		usage = Info.COMMAND_PING_USAGE;
		description = Info.COMMAND_PING_DESCRIPTION;
		sideGate = SideGate.ANYTHING;
		registerParameterDescription("[player]", "the player to ping (or multiple)");
		registerParameterDescription("[opts]", "-t or --top to view totals\n-a or --all to view all pings");
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
				else if(args[0].equalsIgnoreCase("-a") || args[0].equalsIgnoreCase("--all"))
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

					if(pings.isEmpty())
					{
						Gate.msgError(sender, "There is no ping data.");
						return;
					}


					sender.sendMessage(Gate.header("All Player Pings", C.AQUA));
					sendPlayerPings(sender, pings.k());
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

	private void sendPlayerPings(CommandSender sender, List<Player> players)
	{
		C aq = Config.STYLE_STRIP_COLOR ? C.WHITE : C.AQUA;
		C gr = Config.STYLE_STRIP_COLOR ? C.WHITE : C.GRAY;
		C yeeet = Config.STYLE_STRIP_COLOR ? C.WHITE : C.YELLOW;

		int i = 0;

		if(sender instanceof Player)
		{
			RTX rtx = new RTX();
			for (Player p : players) {
				++i;
				RTEX rtex = new RTEX();
				rtex.getExtras().add(new ColoredString(aq, p.getName()));
				rtex.getExtras().add(new ColoredString(gr, Lang.getString("command.ping.all.info").replaceAll("%ping%", getPlayerPing(p))));
				rtex.getExtras().add(new ColoredString(gr, Lang.getString("command.ping.all.measured").replaceAll("%time%", F.time(M.ms() - React.instance.protocolController.ago(p), 0))));
				rtex.getExtras().add(new ColoredString(C.RESET, "\n\n"));
				rtex.getExtras().add(new ColoredString(yeeet, Lang.getString("command.ping.all.tp")));
				rtx.addTextFireHoverCommand(new ColoredString(i % 2 == 0 ? aq : gr, p.getName()).toString() + " ", rtex,
						"/tp " + p.getName(), aq);

			}
			rtx.tellRawTo((Player) sender);
		}

		else
		{
			for (Player p : players) {
				sender.sendMessage(
					(new ColoredString(aq, p.getName())).toString() + gr + ": " +
						(new ColoredString(gr, Lang.getString("command.ping.all.info").replaceAll("%ping%", getPlayerPing(p)))).toString().replace('\n', ' ').trim()
				);
			}
		}
	}

	private String getPlayerPing(Player p)
	{
		C re = Config.STYLE_STRIP_COLOR ? C.WHITE : C.RED;
		C go = Config.STYLE_STRIP_COLOR ? C.WHITE : C.GOLD;
		C gre = Config.STYLE_STRIP_COLOR ? C.WHITE : C.GREEN;
		Double ping = React.instance.protocolController.getPings().get(p);
		ping = ping == null ? 0 : ping;

		return (ping < 300 ? gre : (ping > 800 ? re : go)) + "" + F.f(ping, 2);
	}

}
