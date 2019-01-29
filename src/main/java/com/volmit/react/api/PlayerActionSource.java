package com.volmit.react.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.util.C;
import com.volmit.react.util.nmp.FrameType;
import com.volmit.react.util.nmp.NMP;

public class PlayerActionSource implements IActionSource
{
	private final Player p;

	public PlayerActionSource(Player p)
	{
		this.p = p;
	}

	public Player getPlayer()
	{
		return p;
	}

	@Override
	public void sendResponse(String r)
	{
		Gate.msg(p, r);
	}

	@Override
	public void sendResponseSuccess(String r)
	{
		if(Config.ADVANCEMENT_NOTIFY && Capability.ADVANCEMENT_NOTIFICATIONS.isCapable())
		{
			NMP.MESSAGE.advance(p, new ItemStack(Material.SLIME_BALL), C.GREEN + "\u2714 " + C.GRAY + r, FrameType.GOAL);
		}

		else
		{
			Gate.msgSuccess(p, r);
		}
	}

	@Override
	public void sendResponseError(String r)
	{
		if(Config.ADVANCEMENT_NOTIFY && Capability.ADVANCEMENT_NOTIFICATIONS.isCapable())
		{
			NMP.MESSAGE.advance(p, new ItemStack(Material.BARRIER), C.RED + "\u2718 " + C.GRAY + r, FrameType.GOAL);
		}

		else
		{
			Gate.msgError(p, r);
		}
	}

	@Override
	public void sendResponseActing(String r)
	{
		if(Config.ADVANCEMENT_NOTIFY && Capability.ADVANCEMENT_NOTIFICATIONS.isCapable())
		{
			NMP.MESSAGE.advance(p, new ItemStack(Material.BOOK_AND_QUILL), C.GOLD + "\u26A0 " + C.GRAY + r, FrameType.GOAL);
		}

		else
		{
			Gate.msgActing(p, r);
		}
	}

	@Override
	public String toString()
	{
		return p.getName();
	}
}
