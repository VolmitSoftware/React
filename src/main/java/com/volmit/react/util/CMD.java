package com.volmit.react.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Quick command utility
 *
 * @author cyberpwn
 */
public class CMD
{
	private String command;
	private CommandSender sender;
	private boolean virtual;

	/**
	 * Crate a command
	 *
	 * @param command
	 *            the command
	 */
	public CMD(String command)
	{
		this.command = command;
		sender = Bukkit.getConsoleSender();
		virtual = false;
	}

	/**
	 * The command should be virtually executed
	 *
	 * @return the chain
	 */
	public CMD virtual()
	{
		virtual = true;
		return this;
	}

	/**
	 * Execute the command
	 *
	 * @param sender
	 *            the sender as the runner
	 */
	public void execute(CommandSender sender)
	{
		this.sender = sender;
		execute();
	}

	/**
	 * Execute the command as the console
	 */
	public void execute()
	{
		if(virtual)
		{
			if(sender instanceof Player)
			{
				Bukkit.getServer().getPluginManager().callEvent(new PlayerCommandPreprocessEvent((Player) sender, command.startsWith("/") ? command : "/" + command));
			}

			else
			{
				Bukkit.dispatchCommand(sender, "phast c " + (command.startsWith("/") ? command.substring(1) : command) + ";");
			}
		}

		else
		{
			Bukkit.dispatchCommand(sender, command);
		}
	}
}
