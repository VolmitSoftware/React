package com.volmit.react.command;

import java.io.File;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.ReactPlugin;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.Amounts;
import com.volmit.react.util.C;
import com.volmit.react.util.Ex;
import com.volmit.react.util.F;
import com.volmit.react.util.Platform;
import com.volmit.volume.lang.collections.GList;

public class CommandEnvironment extends ReactCommand
{
	public CommandEnvironment()
	{
		command = Info.COMMAND_ENV;
		aliases = new String[] {Info.COMMAND_ENV_ALIAS_1, Info.COMMAND_ENV_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.MONITOR_ENVIRONMENT.getNode()};
		usage = Info.COMMAND_ENV_USAGE;
		description = Info.COMMAND_ENV_DESCRIPTION;
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
		sender.sendMessage(Gate.header("Environment", C.AQUA));
		sender.sendMessage(ChatColor.GREEN + "Version: " + ChatColor.WHITE + "v" + ReactPlugin.i.getDescription().getVersion());
		sender.sendMessage(ChatColor.GREEN + "Distro: " + ChatColor.WHITE + "Production");
		sender.sendMessage(ChatColor.GREEN + "Operating System: " + ChatColor.WHITE + Platform.getName() + " " + ChatColor.GRAY + "(" + Platform.getVersion() + ")");
		sender.sendMessage(ChatColor.GREEN + "Java: " + ChatColor.WHITE + Platform.ENVIRONMENT.getJavaVendor() + " " + ChatColor.GRAY + "(" + Platform.ENVIRONMENT.getJavaVersion() + ")");

		sender.sendMessage(Gate.header("CPU", C.AQUA));
		sender.sendMessage(ChatColor.GREEN + "Type: " + ChatColor.WHITE + Amounts.to(Platform.CPU.getAvailableProcessors()) + " Core " + Platform.CPU.getArchitecture());
		sender.sendMessage(ChatColor.GREEN + "Utilization: " + ChatColor.WHITE + F.pc(Platform.CPU.getCPULoad(), 1));
		sender.sendMessage(ChatColor.GREEN + "Process Usage: " + ChatColor.WHITE + F.pc(Platform.CPU.getProcessCPULoad(), 1));

		sender.sendMessage(Gate.header("Memory", C.AQUA));
		sender.sendMessage(ChatColor.GREEN + "Physical: " + ChatColor.WHITE + F.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory()) + ChatColor.GRAY + " (" + F.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory()) + " / " + F.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory()) + ")");
		sender.sendMessage(ChatColor.GREEN + "Swap: " + ChatColor.WHITE + F.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory()) + ChatColor.GRAY + " (" + F.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory()) + " / " + F.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory()) + ")");
		sender.sendMessage(ChatColor.GREEN + "Commit: " + ChatColor.WHITE + F.memSize(Platform.MEMORY.VIRTUAL.getCommittedVirtualMemory()));

		sender.sendMessage(Gate.header("Storage", C.AQUA));
		sender.sendMessage(ChatColor.GREEN + "Total: " + ChatColor.WHITE + F.memSize(Platform.STORAGE.getAbsoluteTotalSpace()) + ChatColor.GRAY + " (" + F.memSize(Platform.STORAGE.getAbsoluteUsedSpace()) + " / " + F.memSize(Platform.STORAGE.getAbsoluteTotalSpace()) + ")");

		for(File i : Platform.STORAGE.getRoots())
		{
			try
			{
				if(new File(".").getCanonicalPath().equals(i.getCanonicalPath()))
				{
					sender.sendMessage(ChatColor.AQUA + i.toString() + ": " + ChatColor.WHITE + F.memSize(Platform.STORAGE.getTotalSpace(i)) + ChatColor.GRAY + " (" + F.memSize(Platform.STORAGE.getUsedSpace(i)) + " / " + F.memSize(Platform.STORAGE.getTotalSpace(i)) + ")");
					continue;
				}
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}

			sender.sendMessage(ChatColor.GREEN + i.toString() + ": " + ChatColor.WHITE + F.memSize(Platform.STORAGE.getTotalSpace(i)) + ChatColor.GRAY + " (" + F.memSize(Platform.STORAGE.getUsedSpace(i)) + " / " + F.memSize(Platform.STORAGE.getTotalSpace(i)) + ")");
		}
	}
}
