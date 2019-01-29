package com.volmit.react.util;

import org.bukkit.Bukkit;

public enum Depend
{
	VAULT("Vault"),
	FAWE("FastAsyncWorldEdit"),
	WORLDEDIT("WorldEdit"),
	PROTOLIB("ProtocolLib"),
	CITIZENS("Citizens");
	
	private String name;
	
	private Depend(String name)
	{
		this.name = name;
	}
	
	public boolean exists()
	{
		return Bukkit.getServer().getPluginManager().getPlugin(name) != null;
	}
}
