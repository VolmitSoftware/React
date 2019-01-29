package com.volmit.react.command;

import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SampledType;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.C;
import com.volmit.react.util.F;
import com.volmit.volume.lang.collections.GList;

public class CommandStatus extends ReactCommand
{
	public CommandStatus()
	{
		command = Info.COMMAND_STATUS;
		aliases = new String[] {Info.COMMAND_STATUS_ALIAS_1, Info.COMMAND_STATUS_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode()};
		usage = Info.COMMAND_STATUS_USAGE;
		description = Info.COMMAND_STATUS_DESCRIPTION;
		sideGate = SideGate.PLAYERS_ONLY;
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
		Player p = (Player) sender;

		int slot = -1;

		if((slot = findSlot(p)) != -1)
		{
			p.getInventory().setItem(slot, makeBook());
			Gate.msgSuccess(p, "Status book given");
		}
	}

	private ItemStack makeBook()
	{
		ItemStack is = new ItemStack(Material.WRITTEN_BOOK);
		BookMeta book = (BookMeta) is.getItemMeta();
		book.setTitle(C.BLACK + "^" + C.AQUA + Lang.getString("command.status.server-status")); //$NON-NLS-1$
		GList<String> set = new GList<String>();
		addStatusPages(set);
		addSpikePages(set);
		book.setPages(set);
		is.setItemMeta(book);

		return is;
	}

	private void addSpikePages(GList<String> set)
	{
		String f = ""; //$NON-NLS-1$
		f += C.DARK_AQUA + C.BOLD.toString() + C.ITALIC + Lang.getString("command.status.server-spikes") + C.RESET; //$NON-NLS-1$

		GList<Integer> ints = React.instance.spikeController.getSpikes().v();
		Collections.sort(ints);
		Collections.reverse(ints);

		while(ints.size() > 12)
		{
			ints.remove(ints.size() - 1);
		}

		for(int i : ints)
		{
			for(String j : React.instance.spikeController.getSpikes().k())
			{
				if(React.instance.spikeController.getSpikes().get(j).equals(i))
				{
					String jn = j.length() > 14 ? j.substring(0, 14) + Lang.getString("command.status.elips") : j; //$NON-NLS-1$
					f += C.GRAY + jn + ": " + C.BLACK + C.BOLD.toString() + i + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
					break;
				}
			}
		}

		set.add(f);
	}

	private void addStatusPages(GList<String> set)
	{
		String f = ""; //$NON-NLS-1$
		f += C.DARK_AQUA + C.BOLD.toString() + C.ITALIC + Lang.getString("command.status.general") + C.RESET; //$NON-NLS-1$
		f += C.GRAY + Lang.getString("command.status.players-online") + Bukkit.getServer().getOnlinePlayers().size() + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		f += C.GRAY + Lang.getString("command.status.chunks-loaded") + React.instance.sampleController.getSampler(SampledType.CHK.toString()).get() + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		f += C.GRAY + Lang.getString("command.status.worlds-loaded") + Bukkit.getWorlds().size() + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		f += C.DARK_AQUA + C.BOLD.toString() + C.ITALIC + Lang.getString("command.status.memory") + C.RESET; //$NON-NLS-1$
		f += C.GRAY + React.instance.sampleController.getSampler(SampledType.MEM.toString()).get() + Lang.getString("command.status.of") + React.instance.sampleController.getSampler(SampledType.MAXMEM.toString()).get() + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		f += C.DARK_AQUA + C.BOLD.toString() + C.ITALIC + Lang.getString("command.status.tick") + C.RESET; //$NON-NLS-1$
		f += C.GRAY + Lang.getString("command.status.tick-usage") + React.instance.sampleController.getSampler(SampledType.TIU.toString()).get() + " (" + F.f(React.instance.sampleController.getSampler(SampledType.TICK.toString()).getValue(), 0) + "ms)\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		set.add(f);
	}

	private int findSlot(Player p)
	{
		int test = p.getInventory().getHeldItemSlot();

		if(p.getInventory().getItem(test) == null || p.getInventory().getItem(test).getType().equals(Material.AIR))
		{
			return test;
		}

		for(int i = 0; i < 9; i++)
		{
			test = i;

			if(p.getInventory().getItem(test) == null || p.getInventory().getItem(test).getType().equals(Material.AIR))
			{
				return test;
			}
		}

		return -1;
	}
}
