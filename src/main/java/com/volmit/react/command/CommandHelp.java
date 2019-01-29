package com.volmit.react.command;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.Config;
import com.volmit.react.Info;
import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.ReactPlugin;
import com.volmit.react.api.ICommand;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.C;
import com.volmit.react.util.ColoredString;
import com.volmit.react.util.F;
import com.volmit.react.util.RTEX;
import com.volmit.react.util.RTX;
import com.volmit.react.util.RawText;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public class CommandHelp extends ReactCommand
{
	public CommandHelp()
	{
		command = Info.COMMAND_HELP;
		aliases = new String[] {Info.COMMAND_HELP_ALIAS_1, Info.COMMAND_HELP_ALIAS_2};
		permissions = new String[] {Permissable.ACCESS.getNode()};
		usage = Info.COMMAND_HELP_USAGE;
		description = Info.COMMAND_HELP_DESCRIPTION;
		sideGate = SideGate.ANYTHING;
		registerParameterDescription(Lang.getString("command.help.page"), Lang.getString("command.help.descriptor-page")); //$NON-NLS-1$ //$NON-NLS-2$
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
		if(sender instanceof Player)
		{
			sendPage(sender, args.length == 0 ? 0 : Integer.valueOf(args[0]) - 1, 8);
		}

		else
		{
			for(ICommand i : getSortedCommands())
			{
				sendCommand(sender, i);
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

		for(ICommand i : getPage(page, maxEntries))
		{
			sendCommand(sender, i);
		}

		sendFooter(sender, page, maxEntries);
	}

	public RTX getBeginningRTX()
	{
		C aq = Config.STYLE_STRIP_COLOR ? C.WHITE : C.AQUA;
		C gr = Config.STYLE_STRIP_COLOR ? C.WHITE : C.GRAY;
		RTX rtx = new RTX();
		RTEX rtex = new RTEX();
		rtex.getExtras().add(new ColoredString(aq, Lang.getString("command.help.descriptor-react"))); //$NON-NLS-1$
		rtex.getExtras().add(new ColoredString(gr, Lang.getString("command.help.or-re"))); //$NON-NLS-1$
		rtx.addTextHover(Lang.getString("command.help.react-cmd"), rtex, aq); //$NON-NLS-1$

		return rtx;
	}

	public void sendCommand(CommandSender sender, ICommand command)
	{
		C aq = Config.STYLE_STRIP_COLOR ? C.WHITE : C.AQUA;
		C gr = Config.STYLE_STRIP_COLOR ? C.WHITE : C.GRAY;
		C re = Config.STYLE_STRIP_COLOR ? C.WHITE : C.RED;
		C go = Config.STYLE_STRIP_COLOR ? C.WHITE : C.GOLD;
		C gre = Config.STYLE_STRIP_COLOR ? C.WHITE : C.GREEN;

		if(sender instanceof Player)
		{
			RTX rtx = getBeginningRTX();
			RTEX desc = new RTEX();
			desc.getExtras().add(new ColoredString(aq, Lang.getString("command.help.aliases"))); //$NON-NLS-1$

			for(String i : command.getAliases())
			{
				desc.getExtras().add(new ColoredString(gr, Lang.getString("command.help.nreact") + i)); //$NON-NLS-1$
			}

			desc.getExtras().add(new ColoredString(aq, Lang.getString("command.help.ndescription"))); //$NON-NLS-1$

			for(String i : F.wrapWords("\"" + command.getDescription() + "\"", 28).split("\n")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			{
				desc.getExtras().add(new ColoredString(gr, "\n" + i)); //$NON-NLS-1$
			}

			rtx.addTextHover(" " + command.getCommand(), desc, gr); //$NON-NLS-1$

			if(command.getUsage().trim().length() > 0)
			{
				for(String i : command.getUsage().trim().split(" ")) //$NON-NLS-1$
				{
					RTEX us = new RTEX();
					C prefix = aq;
					ColoredString descx = new ColoredString(aq, Lang.getString("command.help.basic-par")); //$NON-NLS-1$
					String des = Lang.getString("command.help.nondescript"); //$NON-NLS-1$

					if(command.getDescriptionForParameter(i.toLowerCase().trim()) != null)
					{
						des = command.getDescriptionForParameter(i.toLowerCase().trim());
					}

					if(i.startsWith("<")) //$NON-NLS-1$
					{
						prefix = re;
						descx = new ColoredString(prefix, Lang.getString("command.help.required-par")); //$NON-NLS-1$
					}

					if(i.startsWith("[")) //$NON-NLS-1$
					{
						prefix = go;
						descx = new ColoredString(prefix, Lang.getString("command.help.optional-par")); //$NON-NLS-1$
					}

					if(i.startsWith("(")) //$NON-NLS-1$
					{
						prefix = gre;
						descx = new ColoredString(prefix, Lang.getString("command.help.mode-par")); //$NON-NLS-1$
					}

					us.getExtras().add(descx);
					us.getExtras().add(new ColoredString(gr, "\n" + F.wrapWords("\"" + des + "\"", 28))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					rtx.addTextHover(" " + i, us, prefix); //$NON-NLS-1$
				}
			}

			rtx.tellRawTo((Player) sender);
		}

		else
		{
			Bukkit.getConsoleSender().sendMessage(C.WHITE + "/react " + aq + command.getCommand() + " " + C.RESET + command.getUsage() + C.RESET + " - " + command.getDescription());
		}
	}

	public void sendHeader(CommandSender sender, int page, int maxEntries)
	{
		if(sender instanceof Player)
		{
			RawText rtx = new RawText();
			C gray = Config.STYLE_STRIP_COLOR ? C.WHITE : C.GRAY;
			String dgray = Config.STYLE_STRIP_COLOR ? RawText.COLOR_WHITE : RawText.COLOR_DARK_GRAY;
			String daq = Config.STYLE_STRIP_COLOR ? RawText.COLOR_WHITE : RawText.COLOR_AQUA;
			rtx.addText(F.repeat(gray + " ", 17), dgray, false, false, true, true, false); //$NON-NLS-1$
			rtx.addText(" " + (page + 1) + Lang.getString("command.help.ofs") + (getPageSize(maxEntries)) + " ", daq); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			rtx.addText(F.repeat(gray + " ", 17), dgray, false, false, true, true, false); //$NON-NLS-1$
			rtx.tellRawTo(ReactPlugin.i, (Player) sender);
		}
	}

	public void sendFooter(CommandSender sender, int page, int maxEntries)
	{
		if(sender instanceof Player)
		{
			RawText rtx = new RawText();
			String dgray = Config.STYLE_STRIP_COLOR ? RawText.COLOR_WHITE : RawText.COLOR_DARK_GRAY;
			String daq = Config.STYLE_STRIP_COLOR ? RawText.COLOR_WHITE : RawText.COLOR_AQUA;
			String dred = Config.STYLE_STRIP_COLOR ? RawText.COLOR_WHITE : RawText.COLOR_RED;

			if(page > 0)
			{
				rtx.addTextWithHoverCommand(Lang.getString("command.help.symbol-prev"), daq, Lang.getString("command.help.rehelpjumper") + (page), Lang.getString("command.help.previous-page"), daq, false, false, false, false, false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			else
			{
				rtx.addTextWithHover(Lang.getString("command.help.symbol-pipe"), dgray, Lang.getString("command.help.previous-page"), dred, false, false, false, false, false); //$NON-NLS-1$ //$NON-NLS-2$
			}

			rtx.addText(F.repeat(C.GRAY + " ", 17), dgray, false, false, true, true, false); //$NON-NLS-1$
			rtx.addText(F.repeat(C.GRAY + " ", 17), dgray, false, false, true, true, false); //$NON-NLS-1$

			if(page < getPageSize(maxEntries) - 1)
			{
				rtx.addTextWithHoverCommand(Lang.getString("command.help.symbol-next"), daq, Lang.getString("command.help.rehelpjumper") + (page + 2), Lang.getString("command.help.next-page"), daq, false, false, false, false, false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			else
			{
				rtx.addTextWithHover(Lang.getString("command.help.symbol-pipe"), dgray, Lang.getString("command.help.next-page"), dred, false, false, false, false, false); //$NON-NLS-1$ //$NON-NLS-2$
			}

			rtx.tellRawTo(ReactPlugin.i, (Player) sender);
		}
	}

	public GList<ICommand> getSortedCommands()
	{
		GMap<String, ICommand> cmds = new GMap<String, ICommand>();

		for(ICommand i : React.instance.commandController.getCommands())
		{
			cmds.put(i.getCommand(), i);
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

	public GList<ICommand> getPage(int page, int maxEntries)
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
}
