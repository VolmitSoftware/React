package com.volmit.react.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.api.Capability;
import com.volmit.react.api.Flavor;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.C;
import com.volmit.react.util.Protocol;
import com.volmit.volume.lang.collections.GList;

public class CommandCapabilities extends ReactCommand
{
	public CommandCapabilities()
	{
		command = Info.COMMAND_CAPABILITIES;
		aliases = new String[] {Info.COMMAND_CAPABILITIES_ALIAS_1, Info.COMMAND_CAPABILITIES_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode()};
		usage = Info.COMMAND_CAPABILITIES_USAGE;
		description = Info.COMMAND_CAPABILITIES_DESCRIPTION;
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
		for(Capability i : Capability.capabilities)
		{
			if(i.isCapable())
			{
				String why = "";

				if(!i.getVersion().equals(Protocol.EARLIEST))
				{
					why += " " + C.GRAY + "mc " + C.LIGHT_PURPLE + i.getVersion().getVersionString() + C.GRAY;
				}

				if(i.getPlugin() != null)
				{
					why += " " + C.GRAY + "plugin " + C.GREEN + i.getPlugin() + C.GRAY;
				}

				if(!i.getFlavor().equals(Flavor.ANY))
				{
					if(i.getFlavor().equals(Flavor.SOGGY_SPIGOT))
					{
						why += " " + C.GRAY + "flavor " + C.AQUA + "Spigot/PaperSpigot" + C.GRAY;
					}

					else
					{
						why += " " + C.GRAY + "flavor " + C.AQUA + i.getFlavor().fancyName() + C.GRAY;
					}
				}

				Gate.msgSuccess(sender, C.WHITE + i.getName() + C.GRAY + "(" + why.substring(1) + C.GRAY + ")");
			}

			else if(!i.isFlavorCapable())
			{
				Gate.msgError(sender, C.RED + i.getName() + C.GRAY + " (requires " + C.WHITE + i.getFlavor().fancyName() + C.GRAY + ")");
			}

			else if(!i.isPluginCapable())
			{
				Gate.msgError(sender, C.RED + i.getName() + C.GRAY + " (requires " + C.WHITE + i.getPlugin() + C.GRAY + ")");
			}

			else if(!i.isVersionCapable())
			{
				Gate.msgError(sender, C.RED + i.getName() + C.GRAY + " (requires " + C.WHITE + i.getVersion().getVersionString() + "+" + C.GRAY + ")");
			}
		}
	}
}
