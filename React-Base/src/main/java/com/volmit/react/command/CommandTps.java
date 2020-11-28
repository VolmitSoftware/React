package com.volmit.react.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.volmit.react.Gate;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SampledType;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.F;

import primal.lang.collection.GList;
import primal.util.text.C;

public class CommandTps extends ReactCommand
{
	public CommandTps()
	{
		command = "tps";
		aliases = new String[] {"tick", "lag", "cp"};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.MONITOR_ENVIRONMENT.getNode()};
		usage = "";
		description = "Displays CPU & Tick information";
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
		showTps(sender);
	}

	public static void showTps(CommandSender sender)
	{
		if(Permissable.ACCESS.has(sender))
		{
			Gate.msgSuccess(sender, "Current TPS: " + C.GREEN + SampledType.TPS.get().get() + C.GRAY + " (" + C.GREEN + F.f(SampledType.TICK.get().getValue(), 1) + "ms" + C.GRAY + ")");
			Gate.msgSuccess(sender, "Current CPU: " + C.GREEN + SampledType.CPU.get().get());
		}
	}
}
