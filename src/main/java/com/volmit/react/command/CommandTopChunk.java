package com.volmit.react.command;

import java.util.Collections;
import java.util.List;

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
import com.volmit.react.util.ColoredString;
import com.volmit.react.util.F;
import com.volmit.react.util.RTEX;
import com.volmit.react.util.RTX;
import com.volmit.volume.lang.collections.GList;

public class CommandTopChunk extends ReactCommand
{
	public CommandTopChunk()
	{
		command = Info.COMMAND_TOPCHUNK;
		aliases = new String[] {Info.COMMAND_TOPCHUNK_ALIAS_1, Info.COMMAND_TOPCHUNK_ALIAS_2, "tc", "lagchunk"};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.MONITOR_CHUNK_BLAME.getNode()};
		usage = Info.COMMAND_TOPCHUNK_USAGE;
		description = Info.COMMAND_TOPCHUNK_DESCRIPTION;
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
		LagMap map = EventController.map;
		GList<LagMapChunk> htl = new GList<LagMapChunk>(map.getChunks().v());
		Collections.sort(htl);
		Collections.reverse(htl);
		int k = 0;

		if(htl.isEmpty())
		{
			Gate.msgActing(sender, "There are no significant chunk events yet.");
			return;
		}

		sender.sendMessage(Gate.header(C.YELLOW));

		for(LagMapChunk i : htl)
		{
			String string = " " + i.getWorld().getName() + " -> [" + i.getX() + ", " + i.getZ() + "] ";
			C color = C.AQUA;

			k++;
			RTX rtx = new RTX();
			RTEX rtex = new RTEX();
			rtex.getExtras().add(new ColoredString(C.AQUA, "Click to teleport"));
			rtx.addTextFireHoverCommand(string, rtex, "/react chunktp " + i.getWorld().getName() + " " + i.getX() + " " + i.getZ(), color);
			rtx.tellRawTo((Player) sender);

			for(ChunkIssue j : i.getHits().k())
			{
				sender.sendMessage("  " + C.GRAY + j.name() + ": " + C.WHITE + F.time(i.getMS(j), 2));
			}

			if(k > 5)
			{
				break;
			}
		}

		sender.sendMessage(Gate.header(C.YELLOW));
	}
}
