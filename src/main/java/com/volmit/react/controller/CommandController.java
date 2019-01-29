package com.volmit.react.controller;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.ReactPlugin;
import com.volmit.react.Surge;
import com.volmit.react.action.ActionPurgeEntities;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.IAction;
import com.volmit.react.api.ICommand;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.PlayerActionSource;
import com.volmit.react.api.RAI;
import com.volmit.react.api.SampledType;
import com.volmit.react.api.SelectorPosition;
import com.volmit.react.api.Side;
import com.volmit.react.api.SideGate;
import com.volmit.react.command.CommandAccept;
import com.volmit.react.command.CommandAccess;
import com.volmit.react.command.CommandAct;
import com.volmit.react.command.CommandCapabilities;
import com.volmit.react.command.CommandChunk;
import com.volmit.react.command.CommandChunkTP;
import com.volmit.react.command.CommandEnvironment;
import com.volmit.react.command.CommandFix;
import com.volmit.react.command.CommandGlasses;
import com.volmit.react.command.CommandHelp;
import com.volmit.react.command.CommandInstallAgent;
import com.volmit.react.command.CommandMap;
import com.volmit.react.command.CommandMem;
import com.volmit.react.command.CommandMonitor;
import com.volmit.react.command.CommandPing;
import com.volmit.react.command.CommandReload;
import com.volmit.react.command.CommandRequests;
import com.volmit.react.command.CommandRevoke;
import com.volmit.react.command.CommandScoreboardMonitor;
import com.volmit.react.command.CommandStatus;
import com.volmit.react.command.CommandSubscribe;
import com.volmit.react.command.CommandTopChunk;
import com.volmit.react.command.CommandUnsubscribe;
import com.volmit.react.command.CommandVersion;
import com.volmit.react.util.A;
import com.volmit.react.util.C;
import com.volmit.react.util.Controller;
import com.volmit.react.util.Ex;
import com.volmit.react.util.F;
import com.volmit.react.util.JSONObject;
import com.volmit.react.xrai.RAIGoal;
import com.volmit.volume.lang.collections.GList;

public class CommandController extends Controller implements Listener, CommandExecutor, TabCompleter
{
	private GList<ICommand> commands = new GList<ICommand>();
	private boolean k;

	@Override
	public void dump(JSONObject object)
	{
		object.put("commands-loaded", commands.size());
	}

	@Override
	public void start()
	{
		begin();
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
		Bukkit.getPluginCommand(Info.COMMAND_REACT).setExecutor(ReactPlugin.i);
		Bukkit.getPluginCommand(Info.COMMAND_RAI).setExecutor(ReactPlugin.i);
	}

	public void begin()
	{
		k = true;
		Bukkit.getPluginCommand(Info.COMMAND_REACT).setExecutor(this);
		Bukkit.getPluginCommand(Info.COMMAND_RAI).setExecutor(this);
		Bukkit.getPluginCommand(Info.COMMAND_REACT).setTabCompleter(this);
		Surge.register(this);
		commands = new GList<ICommand>();
		commands.add(new CommandInstallAgent());
		commands.add(new CommandAccept());
		commands.add(new CommandChunk());
		commands.add(new CommandAccess());
		commands.add(new CommandAct());
		commands.add(new CommandScoreboardMonitor());
		commands.add(new CommandCapabilities());
		commands.add(new CommandChunkTP());
		commands.add(new CommandEnvironment());
		commands.add(new CommandFix());
		commands.add(new CommandGlasses());
		commands.add(new CommandHelp());
		commands.add(new CommandMap());
		commands.add(new CommandMonitor());
		commands.add(new CommandPing());
		commands.add(new CommandReload());
		commands.add(new CommandRequests());
		commands.add(new CommandRevoke());
		commands.add(new CommandStatus());
		commands.add(new CommandSubscribe());
		commands.add(new CommandTopChunk());
		commands.add(new CommandUnsubscribe());
		commands.add(new CommandVersion());
		commands.add(new CommandMem());
	}

	@Override
	public void tick()
	{

	}

	public void msg(CommandSender s, String msg)
	{
		Gate.msg(s, msg);
	}

	public void f(CommandSender s, String msg)
	{
		Gate.msgError(s, msg);
	}

	@Override
	public boolean onCommand(CommandSender s, Command c, String n, String[] a)
	{
		if(c.getName().equalsIgnoreCase(Info.COMMAND_RAI))
		{
			if(!Permissable.ACCESS.has(s))
			{
				f(s, Info.MSG_PERMISSION);
				return true;
			}

			if(!Permissable.RAI_ACCESS.has(s))
			{
				f(s, Info.MSG_PERMISSION);
				return true;
			}

			if(a.length == 0)
			{
				s.sendMessage(Gate.header(C.AQUA + "RAI", C.LIGHT_PURPLE)); //$NON-NLS-1$
				s.sendMessage(C.LIGHT_PURPLE + "/rai " + C.WHITE + "toggle" + C.GRAY + " - Toggle RAI on or off"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				s.sendMessage(C.LIGHT_PURPLE + "/rai " + C.WHITE + "goals" + C.GRAY + " - Get RAI's Status"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				s.sendMessage(C.LIGHT_PURPLE + "/rai " + C.WHITE + "goals export" + C.GRAY + " - Export Goals"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				s.sendMessage(C.LIGHT_PURPLE + "/rai " + C.WHITE + "goals import <code/url>" + C.GRAY + " - Import Goals"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				s.sendMessage(Gate.header(C.LIGHT_PURPLE));
			}

			else if(a[0].equalsIgnoreCase("toggle")) //$NON-NLS-1$
			{
				if(!Permissable.RAI_CONTROL.has(s))
				{
					f(s, Info.MSG_PERMISSION);
					return true;
				}

				React.instance.raiController.raiEnabled = !React.instance.raiController.raiEnabled;
				Gate.msgRAI(s, React.instance.raiController.raiEnabled ? Lang.getString("message.rai-online") : Lang.getString("message.rai-offline")); //$NON-NLS-1$ //$NON-NLS-2$
			}

			else if(a[0].equalsIgnoreCase("goals")) //$NON-NLS-1$
			{
				if(!Permissable.RAI_MONITOR.has(s))
				{
					f(s, Info.MSG_PERMISSION);
					return true;
				}

				if(a.length >= 2)
				{
					if(a[1].equalsIgnoreCase("export"))
					{
						new A()
						{
							@Override
							public void run()
							{
								Gate.msgRAI(s, "Goals Exported: " + C.WHITE + RAI.instance.getGoalManager().saveGoalsToPaste());
							}
						};

						return true;
					}

					if(a[1].equalsIgnoreCase("import") && a.length == 3)
					{
						String url = a[2];

						new A()
						{
							@Override
							public void run()
							{
								Gate.msgRAI(s, "Imported " + RAI.instance.getGoalManager().loadGoalsFromPaste(url) + " goals.");
							}
						};

						return true;
					}
				}

				s.sendMessage(Gate.header(C.LIGHT_PURPLE + Lang.getString("message.rai-status"), C.LIGHT_PURPLE)); //$NON-NLS-1$

				for(RAIGoal i : RAI.instance.getGoalManager().getGoals())
				{
					s.sendMessage((i.isEnabled() ? C.GREEN + "|" : C.RED + "|") + C.WHITE + i.getName() + C.GRAY + " " + F.f(i.getHealth()) + " (" + F.pc(i.getHealthUtilization(), 0) + ") " + C.WHITE + (i.getMaxUses() > 0 ? "+ " + i.getMaxUses() : ""));
				}

				s.sendMessage(Gate.header(C.LIGHT_PURPLE));
			}
		}

		else if(c.getName().equalsIgnoreCase(Info.COMMAND_REACT))
		{
			Gate.pulse();

			boolean plr = s instanceof Player;
			Player px = plr ? (Player) s : null;

			if(!(a.length >= 1 && (a[0].equalsIgnoreCase(Info.COMMAND_PING) || a[0].equalsIgnoreCase(Info.COMMAND_PING_ALIAS_1) || a[0].equalsIgnoreCase(Info.COMMAND_PING_ALIAS_2) || a[0].equalsIgnoreCase(Info.COMMAND_TEMPACCESS) || a[0].equalsIgnoreCase(Info.COMMAND_TEMPACCESS_ALIAS_1) || a[0].equalsIgnoreCase(Info.COMMAND_TEMPACCESS_ALIAS_2))))
			{
				if(!Permissable.ACCESS.has(s))
				{
					f(s, Info.MSG_PERMISSION);
					return true;
				}
			}

			if(a.length == 0)
			{
				onCommand(plr ? px : s, c, n, new String[] {"?"}); //$NON-NLS-1$
				return true;
			}

			if(a.length == 1 && a[0].length() == 1 && Character.isDigit(a[0].charAt(0)))
			{
				try
				{
					Integer ii = Integer.valueOf(a[0]);
					onCommand(plr ? px : s, c, n, new String[] {"?", ii + ""}); //$NON-NLS-1$
					return true;
				}

				catch(Throwable e)
				{
					Ex.t(e);
				}
			}

			ICommand cmd = null;

			for(ICommand i : commands)
			{
				if(i.getCommand().equalsIgnoreCase(a[0]))
				{
					cmd = i;
					break;
				}
			}

			if(cmd == null)
			{
				br1: for(ICommand i : commands)
				{
					for(String j : i.getAliases())
					{
						if(j.equalsIgnoreCase(a[0]))
						{
							cmd = i;
							break br1;
						}
					}
				}
			}

			if(cmd != null)
			{
				Side side = Side.get(s);
				SideGate gate = cmd.getSideGate();

				if(!gate.supports(side))
				{
					f(s, Lang.getString("message.failure.does-not-support-side") + side.ss()); //$NON-NLS-1$
					return true;
				}
			}

			if(cmd != null)
			{
				if(a.length == 1)
				{
					cmd.fire(plr ? px : s, new String[0]);
				}

				else
				{
					String[] args = new String[a.length - 1];

					for(int i = 1; i < a.length; i++)
					{
						args[i - 1] = a[i];
					}

					cmd.fire(plr ? px : s, args);
				}

				return true;
			}

			else if(a.length > 0)
			{
				IAction aa = null;

				for(IAction i : React.instance.actionController.getActions())
				{
					if(i.getName().equalsIgnoreCase(a[0]))
					{
						aa = i;
						break;
					}
				}

				if(aa == null)
				{
					searching: for(IAction i : React.instance.actionController.getActions())
					{
						for(String j : i.getNodes())
						{
							if(a[0].equalsIgnoreCase(j))
							{
								aa = i;
								break searching;
							}
						}

					}
				}

				if(aa != null)
				{
					GList<String> acts = new GList<String>();
					acts.add("a");
					acts.addAll(new GList<String>(a));

					onCommand(plr ? px : s, c, n, acts.toArray(new String[acts.size()])); // $NON-NLS-1$
				}

				else
				{
					onCommand(plr ? px : s, c, n, new String[] {"?"}); //$NON-NLS-1$
				}
			}

			else
			{
				onCommand(plr ? px : s, c, n, new String[] {"?"}); //$NON-NLS-1$
			}
		}

		return false;
	}

	@EventHandler
	public void on(PlayerCommandPreprocessEvent e)
	{
		if(e.getMessage().equalsIgnoreCase("/killall all"))
		{
			if(!Config.COMMANDOVERRIDES_KILLALL)
			{
				return;
			}

			e.setCancelled(true);

			SelectorPosition sel = new SelectorPosition();
			sel.add(e.getPlayer().getWorld());
			React.instance.actionController.fire(ActionType.PURGE_ENTITIES, new PlayerActionSource(e.getPlayer()), sel);
		}

		if(e.getMessage().equalsIgnoreCase("/killall all -f"))
		{
			if(!Config.COMMANDOVERRIDES_KILLALL_EVERYTHING)
			{
				e.setCancelled(true);

				SelectorPosition sel = new SelectorPosition();
				sel.add(e.getPlayer().getWorld());

				((ActionPurgeEntities) React.instance.actionController.getAction(ActionType.PURGE_ENTITIES)).setForceful(true);
				React.instance.actionController.fire(ActionType.PURGE_ENTITIES, new PlayerActionSource(e.getPlayer()), sel);

				return;
			}
		}

		if(e.getMessage().toLowerCase().equalsIgnoreCase("/tps") || e.getMessage().toLowerCase().equalsIgnoreCase("/lag"))
		{
			if(Permissable.TPS.has(e.getPlayer()) && Config.COMMANDOVERRIDES_TPS)
			{
				Gate.msgSuccess(e.getPlayer(), "Current TPS: " + C.GREEN + SampledType.TPS.get().get() + C.GRAY + " (" + C.GREEN + F.f(SampledType.TICK.get().getValue(), 1) + "ms" + C.GRAY + ")");
				e.setCancelled(true);
			}
		}

		if(e.getMessage().toLowerCase().equalsIgnoreCase("/mem") || e.getMessage().toLowerCase().equalsIgnoreCase("/memory") || e.getMessage().toLowerCase().equalsIgnoreCase("/gc"))
		{
			if(Config.COMMANDOVERRIDES_MEMORY)
			{
				CommandMem.showMem(e.getPlayer());
				e.setCancelled(true);
			}
		}
	}

	public GList<ICommand> getCommands()
	{
		return commands;
	}

	public void setCommands(GList<ICommand> commands)
	{
		this.commands = commands;
	}

	public boolean isK()
	{
		return k;
	}

	public void setK(boolean k)
	{
		this.k = k;
	}

	@Override
	public int getInterval()
	{
		return 1224;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
	{
		GList<String> o = new GList<String>();

		if(args.length == 0)
		{
			for(ICommand i : getCommands())
			{
				o.add(i.getCommand());
			}
		}

		else if(args.length == 1)
		{
			for(ICommand i : getCommands())
			{
				if(i.getCommand().startsWith(args[0].toLowerCase()))
				{
					o.add(i.getCommand());
				}

				for(String j : i.getAliases())
				{
					if(j.startsWith(args[0].toLowerCase()))
					{
						o.add(j);
					}
				}
			}
		}

		else if(args.length == 2)
		{
			String cmdc = args[0];

			for(ICommand i : getCommands())
			{
				if(i.getCommand().equalsIgnoreCase(cmdc))
				{
					o.addAll(i.onTabComplete(sender, command, alias, args));
				}

				for(String j : i.getAliases())
				{
					if(j.equalsIgnoreCase(cmdc))
					{
						o.addAll(i.onTabComplete(sender, command, alias, args));
					}
				}
			}
		}

		return o;
	}
}
