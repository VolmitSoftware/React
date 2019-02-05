package com.volmit.react.api;

import org.bukkit.command.CommandSender;

import primal.lang.collection.GSet;

public interface ISelector
{
	public SelectionMode getMode();

	public Class<?> getType();

	public boolean can(Object o);

	public GSet<Object> getList();

	public GSet<Object> getPossibilities();

	public int parse(CommandSender sender, String input) throws SelectorParseException;

	public String getName();
}
