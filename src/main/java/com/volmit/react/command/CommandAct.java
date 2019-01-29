package com.volmit.react.command;

import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.ReactPlugin;
import com.volmit.react.api.Action;
import com.volmit.react.api.ActionHandle;
import com.volmit.react.api.ActionState;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.ConsoleActionSource;
import com.volmit.react.api.IAction;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.PlayerActionSource;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.Selector;
import com.volmit.react.api.SelectorParseException;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.C;
import com.volmit.react.util.ColoredString;
import com.volmit.react.util.Ex;
import com.volmit.react.util.F;
import com.volmit.react.util.RTEX;
import com.volmit.react.util.RTX;
import com.volmit.react.util.RawText;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public class CommandAct extends ReactCommand
{
	public CommandAct()
	{
		command = Info.COMMAND_ACT;
		aliases = new String[] {Info.COMMAND_ACT_ALIAS_1, Info.COMMAND_ACT_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode()};
		usage = Info.COMMAND_ACT_USAGE;
		description = Info.COMMAND_ACT_DESCRIPTION;
		sideGate = SideGate.ANYTHING;
		registerParameterDescription("<action>", Lang.getString("command.act.action-par-desc")); //$NON-NLS-1$ //$NON-NLS-2$
		registerParameterDescription("[options]", Lang.getString("command.act.options-par-desc")); //$NON-NLS-1$ //$NON-NLS-2$
		registerParameterDescription("[force]", Lang.getString("command.act.force-par-desc") + C.RED + Lang.getString("command.act.force-against-conf-rules")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Override
	public List<String> onTabComplete(CommandSender arg0, Command arg1, String arg2, String[] arg3)
	{
		GList<String> l = new GList<String>();

		for(ActionType i : ActionType.values())
		{
			for(String j : React.instance.actionController.getAction(i).getNodes())
			{
				if(j.toLowerCase().toLowerCase().startsWith(arg3[1]))
				{
					l.add(j);
				}
			}
		}

		return l;
	}

	@Override
	public void fire(CommandSender sender, String[] args)
	{
		if(args.length == 0)
		{
			sendPage(sender, 0, 8);
			return;
		}

		boolean force = false;
		GList<String> ar = new GList<String>(args);

		if(ar.get(ar.last()).equalsIgnoreCase("--force") || ar.get(ar.last()).equalsIgnoreCase("-f")) //$NON-NLS-1$ //$NON-NLS-2$
		{
			force = true;
			ar.remove(ar.last());
			args = ar.toArray(new String[ar.size()]);
		}

		if(args.length == 1)
		{
			try
			{
				int k = Integer.valueOf(args[0]);
				sendPage(sender, k - 1, 8);
				return;
			}

			catch(NumberFormatException e)
			{

			}
		}

		String tag = args[0];
		ISelector[] selectors = new ISelector[args.length - 1];
		GList<String> errors = new GList<String>();
		IAction action = null;

		seeking: for(IAction i : React.instance.actionController.getActions())
		{
			for(String j : i.getNodes())
			{
				if(j.equalsIgnoreCase(tag))
				{
					action = i;
					break seeking;
				}
			}
		}

		if(action == null)
		{
			if(args.length >= 1)
			{
				if(args[0].equalsIgnoreCase("queue") || args[0].equalsIgnoreCase("q") && Permissable.ACT.has(sender))
				{
					if(args.length > 1)
					{
						String cmd = args[1];

						if(cmd.equalsIgnoreCase("x") || cmd.equalsIgnoreCase("s") || cmd.equalsIgnoreCase("stop") || cmd.equalsIgnoreCase("clear"))
						{
							React.instance.actionController.clearQueue(sender);
						}
					}

					else
					{
						React.instance.actionController.displayQueue(sender);
					}
				}

				else
				{
					Gate.msg(sender, Lang.getString("command.act.unknown-action") + C.RED + tag); //$NON-NLS-1$
					return;
				}
			}

			else
			{
				Gate.msg(sender, Lang.getString("command.act.unknown-action") + C.RED + tag); //$NON-NLS-1$
				return;
			}
		}

		if(action == null)
		{
			return;
		}

		if(!sender.hasPermission("react.act." + ((Action) action).getNode()))
		{
			f(sender, Info.MSG_PERMISSION);
			return;
		}

		if(action.getHandleType().equals(ActionHandle.AUTOMATIC))
		{
			Gate.msg(sender, C.RED + Lang.getString("command.act.action") + action.getName() + Lang.getString("command.act.no-manual")); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}

		if(args.length > 0)
		{
			for(int i = 0; i < args.length - 1; i++)
			{
				try
				{
					String val = args[i + 1].toLowerCase().trim();
					selectors[i] = Selector.createSelector(sender, val);
					Selector.pop();

					if(!action.getDefaultSelectors().k().contains(selectors[i].getType()))
					{
						Gate.msg(sender, C.RED + Lang.getString("command.act.action") + action.getName() + Lang.getString("command.act.no-selector") + selectors[i].getName()); //$NON-NLS-1$ //$NON-NLS-2$
						return;
					}
				}

				catch(SelectorParseException e)
				{
					errors.add(e.getMessage());
				}
			}
		}

		if(errors.isEmpty())
		{
			IActionSource source = new ConsoleActionSource();

			if(sender instanceof Player)
			{
				source = new PlayerActionSource((Player) sender);
			}

			if(action.getState().equals(ActionState.RUNNING))
			{
				Gate.msgError(sender, action.getName() + " " + C.AQUA + action.getStatus()); //$NON-NLS-1$
				Gate.msgSuccess(sender, Lang.getString("command.act.queued-to-run")); //$NON-NLS-1$
			}

			((Action) action).setForceful(force);
			React.instance.actionController.fire(action.getType(), source, selectors);
		}

		else
		{
			Gate.msg(sender, C.RED + Lang.getString("command.act.action") + action.getName() + Lang.getString("command.act.failed-to-parse")); //$NON-NLS-1$ //$NON-NLS-2$

			for(String i : errors)
			{
				Gate.msg(sender, C.RED + action.getName() + ": " + i); //$NON-NLS-1$
			}
		}
	}

	public void sendPage(CommandSender sender, int page, int maxEntries)
	{
		if(page < 0)
		{
			return;
		}

		if(page >= getPageSize(maxEntries))
		{
			return;
		}

		sender.sendMessage("  "); //$NON-NLS-1$
		sendHeader(sender, page, maxEntries);

		for(ActionType i : getPage(page, maxEntries))
		{
			sendCommand(sender, i);
		}

		sendFooter(sender, page, maxEntries);
	}

	public RTX getBeginningRTX()
	{
		RTX rtx = new RTX();
		RTEX rtex = new RTEX();
		rtex.getExtras().add(new ColoredString(C.AQUA, Lang.getString("command.help.descriptor-react"))); //$NON-NLS-1$
		rtex.getExtras().add(new ColoredString(C.GRAY, Lang.getString("command.help.or-re"))); //$NON-NLS-1$
		rtx.addTextHover(Lang.getString("command.help.react-cmd") + " act", rtex, C.AQUA); //$NON-NLS-1$ //$NON-NLS-2$

		return rtx;
	}

	public void sendCommand(CommandSender sender, ActionType command)
	{
		if(sender instanceof Player)
		{
			RTX rtx = getBeginningRTX();
			RTEX desc = new RTEX();
			desc.getExtras().add(new ColoredString(C.AQUA, Lang.getString("command.help.aliases"))); //$NON-NLS-1$

			for(String i : React.instance.actionController.getAction(command).getNodes())
			{
				desc.getExtras().add(new ColoredString(C.GRAY, Lang.getString("command.help.nreact") + "act " + i)); //$NON-NLS-1$ //$NON-NLS-2$
			}

			desc.getExtras().add(new ColoredString(C.AQUA, Lang.getString("command.help.ndescription"))); //$NON-NLS-1$

			for(String i : F.wrapWords("\"" + command.getDescription() + "\"", 28).split("\n")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{
				desc.getExtras().add(new ColoredString(C.GRAY, "\n" + i)); //$NON-NLS-1$
			}

			rtx.addTextHover(" " + React.instance.actionController.getAction(command).getNodes()[0], desc, C.GRAY); //$NON-NLS-1$
			IAction a = React.instance.actionController.getAction(command);

			for(Class<?> i : a.getDefaultSelectors().k())
			{
				if(i.equals(Chunk.class))
				{
					RTEX rtex = new RTEX();
					rtex.getExtras().add(new ColoredString(C.GREEN, Lang.getString("command.act.selhelp.pos-selector"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, "\"" + F.wrapWords(Lang.getString("command.act.selhelp.chunks"), 27) + "\"\n")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					rtex.getExtras().add(new ColoredString(C.GREEN, Lang.getString("command.act.selhelp.default"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, "@c:*\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GREEN, Lang.getString("command.act.selhelp.example"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.WHITE, "@c:this\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, Lang.getString("command.act.selhelp.selects-chunk"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.WHITE, "@c:this+2\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, F.wrapWords(Lang.getString("command.act.selhelp.chunrad"), 27) + "\n")); //$NON-NLS-1$ //$NON-NLS-2$
					rtex.getExtras().add(new ColoredString(C.WHITE, "@c:look+2\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, F.wrapWords(Lang.getString("command.act.selhelp.sellook"), 27) + "\n")); //$NON-NLS-1$ //$NON-NLS-2$
					rtex.getExtras().add(new ColoredString(C.WHITE, "@c:*&!this+2\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, F.wrapWords(Lang.getString("command.act.selhelp.selexeyours"), 27))); //$NON-NLS-1$

					rtx.addTextHover(" @c", rtex, C.GREEN); //$NON-NLS-1$
				}

				if(i.equals(EntityType.class))
				{
					RTEX rtex = new RTEX();
					rtex.getExtras().add(new ColoredString(C.AQUA, Lang.getString("command.act.selhelp.entity-sel"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, "\"" + F.wrapWords(Lang.getString("command.act.selhelp.supports-entity"), 27) + "\"\n")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					rtex.getExtras().add(new ColoredString(C.AQUA, Lang.getString("command.act.selhelp.default"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, "@e:*&!(crst)\n")); //$NON-NLS-1$

					rtex.getExtras().add(new ColoredString(C.AQUA, Lang.getString("command.act.selhelp.example"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.WHITE, "@e:Pig\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, Lang.getString("command.act.selhelp.selectspigs"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.WHITE, "@e:Pig&Cow\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, F.wrapWords(Lang.getString("command.act.selhelp.selpigcows"), 27) + "\n")); //$NON-NLS-1$ //$NON-NLS-2$
					rtex.getExtras().add(new ColoredString(C.WHITE, "@e:*&!Cow&!Pig\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, F.wrapWords(Lang.getString("command.act.selhelp.selallpig"), 27))); //$NON-NLS-1$

					rtx.addTextHover(" @e", rtex, C.AQUA); //$NON-NLS-1$
				}

				if(i.equals(Long.class))
				{
					RTEX rtex = new RTEX();
					rtex.getExtras().add(new ColoredString(C.GOLD, Lang.getString("command.act.selhelp.time-sel"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, "\"" + F.wrapWords(Lang.getString("command.act.selhelp.supports-time"), 27) + "\"\n")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					rtex.getExtras().add(new ColoredString(C.GOLD, Lang.getString("command.act.selhelp.default"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, "@t:(udf)s\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GOLD, Lang.getString("command.act.selhelp.example"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.WHITE, "@t:5s\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, Lang.getString("command.act.selhelp.selfs"))); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.WHITE, "@t:20t\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, F.wrapWords(Lang.getString("command.act.selhelp.seltwt"), 27) + "\n")); //$NON-NLS-1$ //$NON-NLS-2$
					rtex.getExtras().add(new ColoredString(C.WHITE, "@t:5h\n")); //$NON-NLS-1$
					rtex.getExtras().add(new ColoredString(C.GRAY, F.wrapWords(Lang.getString("command.act.selhelp.selfh"), 27))); //$NON-NLS-1$

					rtx.addTextHover(" @t", rtex, C.GOLD); //$NON-NLS-1$
				}
			}

			rtx.tellRawTo((Player) sender);
		}
	}

	public void sendHeader(CommandSender sender, int page, int maxEntries)
	{
		if(sender instanceof Player)
		{
			RawText rtx = new RawText();

			rtx.addText(F.repeat(C.GRAY + " ", 17), RawText.COLOR_DARK_GRAY, false, false, true, true, false); //$NON-NLS-1$
			rtx.addText(" " + (page + 1) + Lang.getString("command.help.ofs") + (getPageSize(maxEntries)) + " ", RawText.COLOR_AQUA); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			rtx.addText(F.repeat(C.GRAY + " ", 17), RawText.COLOR_DARK_GRAY, false, false, true, true, false); //$NON-NLS-1$

			rtx.tellRawTo(ReactPlugin.i, (Player) sender);
		}
	}

	public void sendFooter(CommandSender sender, int page, int maxEntries)
	{
		if(sender instanceof Player)
		{
			RawText rtx = new RawText();

			if(page > 0)
			{
				rtx.addTextWithHoverCommand(Lang.getString("command.help.symbol-prev"), RawText.COLOR_AQUA, "/re a " + (page), Lang.getString("command.help.previous-page"), RawText.COLOR_AQUA, false, false, false, false, false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			else
			{
				rtx.addTextWithHover(Lang.getString("command.help.symbol-pipe"), RawText.COLOR_DARK_GRAY, Lang.getString("command.help.previous-page"), RawText.COLOR_RED, false, false, false, false, false); //$NON-NLS-1$ //$NON-NLS-2$
			}

			rtx.addText(F.repeat(C.GRAY + " ", 17), RawText.COLOR_DARK_GRAY, false, false, true, true, false); //$NON-NLS-1$
			rtx.addText(F.repeat(C.GRAY + " ", 17), RawText.COLOR_DARK_GRAY, false, false, true, true, false); //$NON-NLS-1$

			if(page < getPageSize(maxEntries) - 1)
			{
				rtx.addTextWithHoverCommand(Lang.getString("command.help.symbol-next"), RawText.COLOR_AQUA, "/re a " + (page + 2), Lang.getString("command.help.next-page"), RawText.COLOR_AQUA, false, false, false, false, false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			else
			{
				rtx.addTextWithHover(Lang.getString("command.help.symbol-pipe"), RawText.COLOR_DARK_GRAY, Lang.getString("command.help.next-page"), RawText.COLOR_RED, false, false, false, false, false); //$NON-NLS-1$ //$NON-NLS-2$
			}

			rtx.tellRawTo(ReactPlugin.i, (Player) sender);
		}
	}

	public GList<ActionType> getSortedCommands()
	{
		GMap<String, ActionType> cmds = new GMap<String, ActionType>();

		for(ActionType i : ActionType.values())
		{
			try
			{
				cmds.put(React.instance.actionController.getAction(i).getNodes()[0], i);
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}

		return cmds.sortV();
	}

	public int getPageSize(int maxEntries)
	{
		int s = getSortedCommands().size();

		if((double) s % (double) maxEntries > 0)
		{
			return 1 + (int) ((double) s / (double) maxEntries);
		}

		return (int) ((double) s / (double) maxEntries);
	}

	public boolean isValidPage(int page, int maxEntries)
	{
		return page < getPageSize(maxEntries) || page >= 0;
	}

	public GList<ActionType> getPage(int page, int maxEntries)
	{
		if(!isValidPage(page, maxEntries))
		{
			return null;
		}

		int start = page * maxEntries;
		int end = start + maxEntries - 1;
		end = getSortedCommands().getIndexOrLast(end);

		return getSortedCommands().grepExplicit(start, end);
	}

	public void f(CommandSender s, String msg)
	{
		Gate.msgError(s, C.RED + msg);
	}
}
