package com.volmit.react.command;

import java.util.Collections;
import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.React;
import com.volmit.react.api.ChunkIssue;
import com.volmit.react.api.LagMap;
import com.volmit.react.api.LagMapChunk;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.controller.EventController;
import com.volmit.react.util.F;
import com.volmit.react.util.FinalInteger;

import primal.bukkit.inventory.Element;
import primal.bukkit.inventory.UIElement;
import primal.bukkit.inventory.UIPaneDecorator;
import primal.bukkit.inventory.UIWindow;
import primal.bukkit.inventory.Window;
import primal.bukkit.sched.J;
import primal.bukkit.world.MaterialBlock;
import primal.lang.collection.GList;
import primal.lang.collection.GMap;
import primal.util.text.C;

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
		if(!(sender instanceof Player))
		{
			Gate.msg(sender, "Ingame only!");
			return;
		}

		Window w = new UIWindow((Player) sender).setDecorator(new UIPaneDecorator(C.DARK_GRAY));
		w.setTitle("Top Chunks");
		w.open();
		FinalInteger fi = new FinalInteger(-1);
		fi.set(J.sr(new Runnable()
		{
			@Override
			public void run()
			{
				if(w.isVisible())
				{
					update(w);
				}

				else
				{
					React.instance.monitorController.removeTop((Player) sender);
					J.csr(fi.get());
				}
			}
		}, 20));
		React.instance.monitorController.addTop((Player) sender);
	}

	private void update(Window w)
	{
		w.clearElements();
		LagMap map = EventController.map;
		GList<LagMapChunk> htl = map.sorted();
		Collections.reverse(htl);
		int index = 0;
		double tmax = 0;

		for(LagMapChunk i : htl)
		{
			double ts = i.totalScore();

			if(ts > tmax)
			{
				tmax = ts;
			}
		}

		for(LagMapChunk i : htl)
		{
			GMap<ChunkIssue, Double> g = i.getPercent();
			Chunk c = i.getC();
			World ww = i.getWorld();
			//@builder
			Element e = new UIElement("ch-"+ index)
					.setName(C.GOLD + ww.getName() + " at " + C.YELLOW + c.getX() + ", " + c.getZ())
					.setMaterial(new MaterialBlock(Material.IRON_CHESTPLATE))
					.setProgress(i.totalScore() / (tmax == 0 ? 1 : tmax))
					.onLeftClick((x) -> tpChunk(c, w.getViewer()))
					.addLore(C.AQUA + "Entities: " + C.DARK_AQUA  + C.BOLD+ F.f(c.getEntities().length))
					.addLore(C.AQUA + "Tiles: " + C.DARK_AQUA  + C.BOLD+ F.f(c.getTileEntities().length));

			for(ChunkIssue j : i.getHits().k())
			{
				e.addLore(C.AQUA + j.toName() + ": " + C.DARK_AQUA + C.BOLD + F.pc(g.get(j), 0) + " Impact: " + F.f((int)(i.getHits().get(j) / 10000D)));
			}

			w.setElement(w.getPosition(index), w.getRow(index), e);
			//@done

			index++;

			if(index > 56)
			{
				break;
			}
		}

		w.updateInventory();
	}

	private void tpChunk(Chunk c, Player v)
	{
		v.teleport(c.getWorld().getHighestBlockAt(c.getBlock(0, 256, 0).getLocation()).getLocation());
	}
}
