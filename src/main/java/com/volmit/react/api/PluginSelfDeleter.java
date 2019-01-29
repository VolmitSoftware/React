package com.volmit.react.api;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;

import com.volmit.react.util.Ex;

public class PluginSelfDeleter extends Thread
{
	private Plugin plugin;
	private File f;
	private Runnable r;

	public PluginSelfDeleter(Plugin p, File f, Runnable runnable)
	{
		this.plugin = p;
		this.f = f;
		this.r = runnable;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run()
	{
		File dfolder = plugin.getDataFolder();
		String name = plugin.getName();
		PluginManager pluginManager = Bukkit.getPluginManager();
		SimpleCommandMap commandMap = null;
		List<Plugin> plugins = null;
		Map<String, Plugin> names = null;
		Map<String, Command> commands = null;
		Map<Event, SortedSet<RegisteredListener>> listeners = null;

		boolean reloadlisteners = true;

		if(pluginManager != null)
		{
			pluginManager.disablePlugin(plugin);

			try
			{
				Field pluginsField = Bukkit.getPluginManager().getClass().getDeclaredField("plugins");
				pluginsField.setAccessible(true);
				plugins = (List<Plugin>) pluginsField.get(pluginManager);
				Field lookupNamesField = Bukkit.getPluginManager().getClass().getDeclaredField("lookupNames");
				lookupNamesField.setAccessible(true);
				names = (Map<String, Plugin>) lookupNamesField.get(pluginManager);

				try
				{
					Field listenersField = Bukkit.getPluginManager().getClass().getDeclaredField("listeners");
					listenersField.setAccessible(true);
					listeners = (Map<Event, SortedSet<RegisteredListener>>) listenersField.get(pluginManager);
				}

				catch(Throwable e)
				{
					Ex.t(e);
					reloadlisteners = true;
				}

				Field commandMapField = Bukkit.getPluginManager().getClass().getDeclaredField("commandMap");
				commandMapField.setAccessible(true);
				commandMap = (SimpleCommandMap) commandMapField.get(pluginManager);
				Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
				knownCommandsField.setAccessible(true);
				commands = (Map<String, Command>) knownCommandsField.get(commandMap);
			}

			catch(Throwable e)
			{
				Ex.t(e);
				return;
			}
		}

		pluginManager.disablePlugin(plugin);

		if(plugins != null && plugins.contains(plugin))
		{
			plugins.remove(plugin);
		}

		if(names != null && names.containsKey(name))
		{
			names.remove(name);
		}

		if(listeners != null && reloadlisteners)
		{
			for(SortedSet<RegisteredListener> set : listeners.values())
			{
				for(Iterator<RegisteredListener> it = set.iterator(); it.hasNext();)
				{
					RegisteredListener value = it.next();
					if(value.getPlugin() == plugin)
					{
						it.remove();
					}
				}
			}
		}

		if(commandMap != null)
		{
			for(Iterator<Map.Entry<String, Command>> it = commands.entrySet().iterator(); it.hasNext();)
			{
				Map.Entry<String, Command> entry = it.next();
				if(entry.getValue() instanceof PluginCommand)
				{
					PluginCommand c = (PluginCommand) entry.getValue();
					if(c.getPlugin() == plugin)
					{
						c.unregister(commandMap);
						it.remove();
					}
				}
			}
		}

		ClassLoader cl = plugin.getClass().getClassLoader();

		if(cl instanceof URLClassLoader)
		{
			try
			{
				((URLClassLoader) cl).close();
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}

		System.gc();

		f.delete();

		if(dfolder.exists())
		{
			del(dfolder);
		}

		r.run();
	}

	private void del(File f)
	{
		if(f.isDirectory())
		{
			for(File i : f.listFiles())
			{
				del(i);
			}

			f.delete();
		}

		else
		{
			f.delete();
		}
	}
}
