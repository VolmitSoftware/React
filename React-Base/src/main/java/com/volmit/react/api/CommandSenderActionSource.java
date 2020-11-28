package com.volmit.react.api;

import org.bukkit.command.CommandSender;

import com.volmit.react.Gate;

public class CommandSenderActionSource implements IActionSource
{
	private CommandSender sender;

	public CommandSenderActionSource(CommandSender sender)
	{
		this.sender = sender;
	}

	public CommandSender getSender()
	{
		return sender;
	}

	@Override
	public void sendResponse(String r)
	{
		Gate.msg(sender, r);
	}

	@Override
	public void sendResponseSuccess(String r)
	{
		Gate.msgSuccess(sender, r);
	}

	@Override
	public void sendResponseError(String r)
	{
		Gate.msgError(sender, r);
	}

	@Override
	public void sendResponseActing(String r)
	{
		Gate.msgActing(sender, r);
	}
}
