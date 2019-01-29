package com.volmit.react.command;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import com.volmit.react.Gate;
import com.volmit.react.React;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SampledType;
import com.volmit.react.api.SideGate;
import com.volmit.react.controller.SampleController;
import com.volmit.react.util.A;
import com.volmit.react.util.C;
import com.volmit.react.util.F;
import com.volmit.react.util.FinalInteger;
import com.volmit.react.util.FinalLong;
import com.volmit.react.util.S;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public class CommandMem extends ReactCommand
{
	public CommandMem()
	{
		command = "memory";
		aliases = new String[] {"mem", "ram", "wam"};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.MONITOR_ENVIRONMENT.getNode()};
		usage = "";
		description = "Displays Memory information";
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
		if(args.length == 1)
		{
			if(args[0].equalsIgnoreCase("worlds"))
			{
				if(!React.instance.memoryController.isRunning())
				{
					Gate.msgError(sender, "Memory Monitor not installed!");
					return;
				}
				Gate.msgActing(sender, "Computing Memory usage for " + Bukkit.getWorlds().size() + " worlds");

				new A()
				{
					@Override
					public void run()
					{
						GMap<World, Long> mem = new GMap<World, Long>();
						int total = Bukkit.getWorlds().size();
						FinalInteger of = new FinalInteger(0);
						FinalLong abs = new FinalLong(0);

						for(World i : Bukkit.getWorlds())
						{
							long v = React.instance.memoryController.getMemoryUsage(i);
							of.add(1);
							abs.add(v);
							mem.put(i, v);
							new S("")
							{
								@Override
								public void run()
								{
									Gate.msgActing(sender, "Computing Sizes: " + C.WHITE + F.pc(((double) of.get()) / (double) total));
								}
							};
						}

						new S("")
						{
							@Override
							public void run()
							{
								for(World i : mem.sortK().reverse())
								{
									sender.sendMessage(C.GRAY + i.getName() + ": " + C.GOLD + F.memSize(mem.get(i), 2));
								}
							}
						};
					}
				};
			}

			else if(args[0].equalsIgnoreCase("top"))
			{
				if(!React.instance.memoryController.isRunning())
				{
					Gate.msgError(sender, "Memory Monitor not installed!");
					return;
				}

				Gate.msgActing(sender, "Computing Memory usage for " + Bukkit.getPluginManager().getPlugins().length + " plugins");

				new A()
				{
					@Override
					public void run()
					{
						GMap<Plugin, Long> mem = new GMap<Plugin, Long>();
						int total = Bukkit.getPluginManager().getPlugins().length;
						FinalInteger of = new FinalInteger(0);
						FinalLong abs = new FinalLong(0);

						for(Plugin i : Bukkit.getPluginManager().getPlugins())
						{
							long v = React.instance.memoryController.getMemoryUsagePlugin(i);
							of.add(1);
							abs.add(v);
							mem.put(i, v);
							new S("")
							{
								@Override
								public void run()
								{
									Gate.msgActing(sender, "Computing Sizes: " + C.WHITE + F.pc(((double) of.get()) / (double) total));
								}
							};
						}

						new S("")
						{
							@Override
							public void run()
							{
								for(Plugin i : mem.sortK().reverse())
								{
									sender.sendMessage(C.GRAY + i.getName() + ": " + C.GOLD + F.memSize(mem.get(i), 2));
								}
							}
						};
					}
				};

				for(Plugin i : Bukkit.getPluginManager().getPlugins())
				{
					if(i.getName().toLowerCase().contains(args[0].toLowerCase()))
					{
						if(React.instance.memoryController.isRunning())
						{
							new A()
							{
								@Override
								public void run()
								{
									long v = React.instance.memoryController.getMemoryUsagePlugin(i);

									new S("")
									{
										@Override
										public void run()
										{
											Gate.msgSuccess(sender, i.getName() + " is using ~" + C.WHITE + F.memSize(v, 2) + C.GRAY + " of memory.");
										}
									};
								}
							};
						}

						else
						{
							Gate.msgError(sender, "Memory Monitor not installed!");
						}

						break;
					}
				}
			}

			else
			{
				if(!React.instance.memoryController.isRunning())
				{
					Gate.msgError(sender, "Memory Monitor not installed!");
					return;
				}

				for(Plugin i : Bukkit.getPluginManager().getPlugins())
				{
					if(i.getName().toLowerCase().contains(args[0].toLowerCase()))
					{
						if(React.instance.memoryController.isRunning())
						{
							new A()
							{
								@Override
								public void run()
								{
									long v = React.instance.memoryController.getMemoryUsagePlugin(i);

									new S("")
									{
										@Override
										public void run()
										{
											Gate.msgSuccess(sender, i.getName() + " is using ~" + C.WHITE + F.memSize(v, 2) + C.GRAY + " of memory.");
										}
									};
								}
							};
						}

						else
						{
							Gate.msgError(sender, "Memory Monitor not installed!");
						}

						break;
					}
				}
			}
		}

		else
		{
			showMem(sender);
		}
	}

	public static void showMem(CommandSender sender)
	{
		if(Permissable.ACCESS.has(sender))
		{
			Gate.msgSuccess(sender, "Current Memory Usage: " + C.GOLD + SampledType.MEM.get().get() + C.GRAY + " (" + C.GOLD + F.pc(SampledType.MEM.get().getValue() / SampledType.MAXMEM.get().getValue(), 0) + C.GRAY + ")");
			Gate.msgSuccess(sender, "Maximum: " + C.GOLD + SampledType.MAXMEM.get().get());
			Gate.msgSuccess(sender, "Allocated: " + C.GOLD + SampledType.ALLOCMEM.get().get());
			Gate.msgSuccess(sender, "Free: " + C.GOLD + SampledType.FREEMEM.get().get());
			Gate.msgSuccess(sender, "Total Memory Allocated: " + C.GOLD + F.memSize(SampleController.m.getTotalAllocated(), 3));
			Gate.msgSuccess(sender, "Total Memory Collected: " + C.GOLD + F.memSize(SampleController.m.getTotalCollected(), 3));
		}
	}
}
