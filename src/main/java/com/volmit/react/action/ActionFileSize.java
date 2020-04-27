package com.volmit.react.action;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.volmit.react.Gate;
import com.volmit.react.ReactPlugin;
import com.volmit.react.api.Action;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.PlayerActionSource;
import com.volmit.react.util.A;
import com.volmit.react.util.DataCluster;
import com.volmit.react.util.F;
import com.volmit.react.util.Paste;
import com.volmit.react.util.S;

import primal.lang.collection.GMap;
import primal.util.text.C;

public class ActionFileSize extends Action
{
	public ActionFileSize()
	{
		super(ActionType.FILE_SIZE);
		setNodes(new String[] {"filesize", "fs", "file"});
	}

	@Override
	public void enact(IActionSource source, ISelector... selectors)
	{
		source.sendResponseActing("Calculating File Sizes Please Wait...");
		GMap<String, Long> map = new GMap<String, Long>();

		File f = new File(".");

		new A()
		{
			@Override
			public void run()
			{
				long tw = 0;

				for(World i : Bukkit.getWorlds())
				{
					long ws = size(i.getWorldFolder());
					map.put("worlds.world." + i.getName().replaceAll(" ", "-"), ws);
					tw += ws;
				}

				for(Plugin i : Bukkit.getPluginManager().getPlugins())
				{
					File fxx = i.getDataFolder();

					if(fxx.exists() && fxx.isDirectory())
					{
						map.put("plugins.plugin-data." + i.getName(), size(fxx));
					}
				}

				map.put("plugins.total", size(ReactPlugin.i.getDataFolder().getParentFile()));
				map.put("everything", size(f));
				map.put("worlds.total", tw);

				DataCluster cc = new DataCluster();

				for(String i : map.k())
				{
					cc.set(i, F.ofSize(map.get(i), 1000, 2));
				}

				String d = cc.toFileConfiguration().saveToString();

				try
				{
					String u = Paste.paste(d);

					new S("respond")
					{
						@Override
						public void run()
						{
							Gate.msgSuccess(((PlayerActionSource) source).getPlayer(), "Ding! " + C.WHITE + C.UNDERLINE + u);
						}
					};
				}

				catch(Exception e)
				{
					e.printStackTrace();

					new S("respond")
					{
						@Override
						public void run()
						{
							source.sendResponseActing(d);
							source.sendResponseError("Failed to paste to Hastebin.");
						}
					};
				}
			}
		};
	}

	public long size(File f)
	{
		long size = 0;

		if(f != null && f.exists())
		{
			if(f.isDirectory())
			{
				for(File i : f.listFiles())
				{
					size += size(i);
				}
			}

			else
			{
				size += f.length();
			}
		}

		return size;
	}

	@Override
	public String getNode()
	{
		return "file-size";
	}

	@Override
	public ItemStack getIcon()
	{
		return new ItemStack(Material.PAPER);
	}
}
