package primal.bukkit.plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import primal.bukkit.command.ICommand;
import primal.bukkit.command.PrimalCommand;
import primal.bukkit.command.RouterCommand;
import primal.bukkit.command.VirtualCommand;
import primal.lang.collection.GList;
import primal.lang.collection.GMap;
import primal.util.reflection.V;

public abstract class PrimalPlugin extends JavaPlugin
{
	public static PrimalPlugin instance;
	private GMap<GList<String>, VirtualCommand> commands;

	public abstract void start();

	public abstract void stop();

	public abstract String getTag(String subTag);

	@Override
	public void onEnable()
	{
		commands = new GMap<>();
		instance = this;

		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(primal.bukkit.command.Command.class))
			{
				try
				{
					i.setAccessible(true);
					PrimalCommand pc = (PrimalCommand) i.getType().getConstructor().newInstance();
					primal.bukkit.command.Command c = i.getAnnotation(primal.bukkit.command.Command.class);
					registerCommand(pc, c.value());
				}

				catch(IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException e)
				{
					e.printStackTrace();
				}
			}
		}

		start();
	}

	@Override
	public void onDisable()
	{
		stop();
		unregisterAll();
		instance = null;
	}

	@Override
	public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args)
	{
		GList<String> chain = new GList<String>().qadd(args);

		for(GList<String> i : commands.k())
		{
			for(String j : i)
			{
				if(j.equalsIgnoreCase(label))
				{
					VirtualCommand cmd = commands.get(i);

					if(cmd.hit(sender, chain.copy()))
					{
						return true;
					}
				}
			}
		}

		return false;
	}

	public void registerCommand(ICommand cmd)
	{
		registerCommand(cmd, "");
	}

	public void registerCommand(ICommand cmd, String subTag)
	{
		commands.put(cmd.getAllNodes(), new VirtualCommand(cmd, subTag.trim().isEmpty() ? getTag() : getTag(subTag.trim())));
		PluginCommand cc = getCommand(cmd.getNode().toLowerCase());

		if(cc != null)
		{
			cc.setExecutor(this);
			cc.setUsage(getName() + ":" + getClass().toString() + ":" + cmd.getNode());
		}

		else
		{
			RouterCommand r = new RouterCommand(cmd, this);
			r.setUsage(getName() + ":" + getClass().toString());
			((CommandMap) new V(Bukkit.getServer()).get("commandMap")).register("", r);
		}
	}

	public void unregisterCommand(ICommand cmd)
	{
		SimpleCommandMap m = new V(Bukkit.getServer()).get("commandMap");
		Map<String, Command> k = new V(m).get("knownCommands");

		for(Iterator<Map.Entry<String, Command>> it = k.entrySet().iterator(); it.hasNext();)
		{
			Map.Entry<String, Command> entry = it.next();
			if(entry.getValue() instanceof Command)
			{
				org.bukkit.command.Command c = (org.bukkit.command.Command) entry.getValue();
				String u = c.getUsage();

				if(u != null && u.equals(getName() + ":" + getClass().toString() + ":" + cmd.getNode()))
				{
					if(c.unregister(m))
					{
						it.remove();
					}

					else
					{
						Bukkit.getConsoleSender().sendMessage(getTag() + "Failed to unregister command " + c.getName());
					}
				}
			}
		}
	}

	public String getTag()
	{
		return getTag("");
	}

	public void registerListener(Listener l)
	{
		Bukkit.getPluginManager().registerEvents(l, this);
	}

	public void unregisterListener(Listener l)
	{
		HandlerList.unregisterAll(l);
	}

	public void unregisterListeners()
	{
		HandlerList.unregisterAll(this);
	}

	public void unregisterCommands()
	{
		for(VirtualCommand i : commands.v())
		{
			unregisterCommand(i.getCommand());
		}
	}

	public void unregisterAll()
	{
		unregisterListeners();
		unregisterCommands();
	}

	public File getDataFile(String... strings)
	{
		File f = new File(getDataFolder(), new GList<String>(strings).toString(File.separator));
		f.getParentFile().mkdirs();
		return f;
	}

	public File getDataFolder(String... strings)
	{
		if(strings.length == 0)
		{
			return super.getDataFolder();
		}

		File f = new File(getDataFolder(), new GList<String>(strings).toString(File.separator));
		f.mkdirs();

		return f;
	}
}
