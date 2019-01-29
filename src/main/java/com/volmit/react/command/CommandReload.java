package com.volmit.react.command;

import java.io.File;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.React;
import com.volmit.react.ReactPlugin;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.Ex;
import com.volmit.volume.lang.collections.GList;

public class CommandReload extends ReactCommand
{
	public CommandReload()
	{
		command = Info.COMMAND_RELOAD;
		aliases = new String[] {Info.COMMAND_RELOAD_ALIAS_1, Info.COMMAND_RELOAD_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.RELOAD.getNode()};
		usage = Info.COMMAND_RELOAD_USAGE;
		description = Info.COMMAND_RELOAD_DESCRIPTION;
		sideGate = SideGate.ANYTHING;
		registerParameterDescription("[options]", "-xconf Reset Configs\n\n-xwconf Reset World Configs\n\n-xcache Wipe Cache\n-xgoals Reset Rai goals");
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
		for(String i : args)
		{
			try
			{
				if(i.equalsIgnoreCase("-xconf"))
				{
					Config.resetConfigs();
					Gate.msgSuccess(sender, "Config wipe scheduled.");
				}

				if(i.equalsIgnoreCase("-xgoals"))
				{
					React.instance.raiController.getRai().getGoalManager().forceResetDefaults();
					Gate.msgSuccess(sender, "Reset goals.");
				}

				if(i.equalsIgnoreCase("-xcache"))
				{
					new File(new File(ReactPlugin.i.getDataFolder(), "cache"), "WIPE").mkdirs();
					Gate.msgSuccess(sender, "Cache wipe scheduled.");
				}

				if(i.equalsIgnoreCase("-xwconf"))
				{
					React.instance.worldController.wipe();
					Gate.msgSuccess(sender, "World config wipe scheduled.");
				}
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}

		ReactPlugin.reload();
		Gate.msgSuccess(sender, "React Reloaded");
	}
}
