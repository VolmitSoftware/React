package com.volmit.react.fix;

import org.bukkit.command.CommandSender;

import com.volmit.react.Gate;
import com.volmit.react.util.C;

public class FixLowMM extends Fix
{
	public FixLowMM()
	{
		setName("Low Memory Mode");
		setId("low-mem");
		setAliases(new String[] {"lmm", "mem"});
		setDescription("Toggles react low memory mode");
		setUsage("");
	}

	@Override
	public void run(CommandSender sender, String[] args)
	{
		Gate.toggleLowMemoryMode();
		Gate.msgSuccess(sender, "Low memory mode: " + C.WHITE + (Gate.isLowMemory() ? "ENABLED" : "DISABLED"));
	}
}
