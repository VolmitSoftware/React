package com.volmit.react.command;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Info;
import com.volmit.react.Lang;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.Ex;
import com.volmit.volume.lang.collections.GList;

public class CommandChunkTP extends ReactCommand
{
	public CommandChunkTP()
	{
		command = Info.COMMAND_CTP;
		aliases = new String[] {Info.COMMAND_CTP_ALIAS_1, Info.COMMAND_CTP_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.TELEPORT.getNode()};
		usage = Info.COMMAND_CTP_USAGE;
		description = Info.COMMAND_CTP_DESCRIPTION;
		sideGate = SideGate.PLAYERS_ONLY;
		registerParameterDescription("<world>", Lang.getString("react.command.chunktp.world")); //$NON-NLS-1$ //$NON-NLS-2$
		registerParameterDescription("<x>", Lang.getString("react.command.chunktp.x")); //$NON-NLS-1$ //$NON-NLS-2$
		registerParameterDescription("<y>", Lang.getString("react.command.chunktp.y")); //$NON-NLS-1$ //$NON-NLS-2$
		registerParameterDescription("<z>", Lang.getString("react.command.chunktp.z")); //$NON-NLS-1$ //$NON-NLS-2$
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
		try
		{
			World world = Bukkit.getWorld(args[0]);
			int x = Integer.valueOf(args[1]);
			int z = Integer.valueOf(args[2]);
			Chunk c = world.getChunkAt(x, z);
			((Player) sender).teleport(world.getHighestBlockAt(c.getBlock(0, 256, 0).getLocation()).getLocation());
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}
}
