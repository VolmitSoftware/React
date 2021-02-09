package com.volmit.react;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.volmit.react.util.PluginUtil;

public class Surge
{
	private static Thread mainThread;

	public static void m()
	{
		mainThread = Thread.currentThread();
	}

	public static boolean isObfuscated()
	{
		return !Surge.class.getSimpleName().equals("Surge");
	}

	public static void register(Listener l)
	{
		Bukkit.getServer().getPluginManager().registerEvents(l, ReactPlugin.i);
	}

	public static void unregister(Listener l)
	{
		HandlerList.unregisterAll(l);
	}

	public static File folder(String f)
	{
		return new File(ReactPlugin.i.getDataFolder(), f);
	}

	public static File folder()
	{
		return ReactPlugin.i.getDataFolder();
	}

	public static File getPluginJarFile()
	{
		File parent = ReactPlugin.i.getDataFolder().getParentFile();
		String plname = PluginUtil.getPluginFileName(ReactPlugin.i.getName());
		return new File(parent, plname);
	}

	public static File getPluginJarFileUnsafe(Plugin main)
	{
		File parent = main.getDataFolder().getParentFile();
		String plname = PluginUtil.getPluginFileNameUnsafe(main.getName(), main);
		return new File(parent, plname);
	}

	public static Thread getServerThread()
	{
		return mainThread;
	}

	public static boolean isMainThread()
	{
		return Bukkit.isPrimaryThread();
	}
}
