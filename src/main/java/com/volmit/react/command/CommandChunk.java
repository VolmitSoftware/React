package com.volmit.react.command;

import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.api.ChunkIssue;
import com.volmit.react.api.LagMap;
import com.volmit.react.api.LagMapChunk;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.controller.EventController;
import com.volmit.react.util.C;
import com.volmit.react.util.F;
import com.volmit.volume.lang.collections.GList;

public class CommandChunk extends ReactCommand
{
	public CommandChunk()
	{
		command = Info.COMMAND_CHUNK;
		aliases = new String[] {Info.COMMAND_CHUNK_ALIAS_1, Info.COMMAND_CHUNK_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.MONITOR.getNode()};
		usage = Info.COMMAND_CHUNK_USAGE;
		description = Info.COMMAND_CHUNK_DESCRIPTION;
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
		Chunk c = ((Player) sender).getLocation().getChunk();
		sender.sendMessage(Gate.header("Chunk " + c.getX() + " " + c.getZ(), C.AQUA));

		sender.sendMessage(C.GRAY + "Entities: " + C.WHITE + C.BOLD + c.getEntities().length);
		sender.sendMessage(C.GRAY + "Tile Entities: " + C.WHITE + C.BOLD + c.getTileEntities().length);

		try
		{
			LagMap lm = EventController.map;

			if(lm.getChunks().containsKey(c))
			{
				LagMapChunk lc = lm.getChunks().get(c);

				for(ChunkIssue i : lc.getHits().k())
				{
					sender.sendMessage(C.GRAY + i.name() + ": " + C.WHITE + C.BOLD + F.f(lc.getMS(i), 2) + "ms");
				}
			}
		}

		catch(Throwable e)
		{

		}

		sender.sendMessage(Gate.header(C.AQUA));
	}
}
