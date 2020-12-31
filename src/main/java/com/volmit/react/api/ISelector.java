package com.volmit.react.api;

import org.bukkit.command.CommandSender;

import primal.lang.collection.GSet;

public interface ISelector
{
	SelectionMode getMode();

	Class<?> getType();

	boolean can(Object o);

	GSet<Object> getList();

	GSet<Object> getPossibilities();

	int parse(CommandSender sender, String input) throws SelectorParseException;

	String getName();
}
