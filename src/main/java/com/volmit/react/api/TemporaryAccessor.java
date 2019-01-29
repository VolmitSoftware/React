package com.volmit.react.api;

import org.bukkit.entity.Player;

import com.volmit.volume.lang.collections.GSet;

public class TemporaryAccessor
{
	private Player player;
	private GSet<Permissable> permissions;

	public TemporaryAccessor(Player player)
	{
		this.player = player;
		permissions = new GSet<Permissable>();
	}

	public Player getPlayer()
	{
		return player;
	}

	public GSet<Permissable> getPermissions()
	{
		return permissions;
	}

	public void addPermission(Permissable perm)
	{
		permissions.add(perm);
	}

	public void addAll()
	{
		for(Permissable i : Permissable.values())
		{
			permissions.add(i);
		}
	}
}
