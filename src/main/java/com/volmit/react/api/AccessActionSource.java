package com.volmit.react.api;

import org.bukkit.entity.Player;

import com.volmit.react.Gate;
import com.volmit.react.util.P;

public class AccessActionSource implements IActionSource
{
	@Override
	public void sendResponse(String r)
	{
		for(Player i : P.onlinePlayers())
		{
			if(Permissable.ACCESS.has(i))
			{
				Gate.msg(i, r);
			}
		}
	}

	@Override
	public void sendResponseSuccess(String r)
	{
		for(Player i : P.onlinePlayers())
		{
			if(Permissable.ACCESS.has(i))
			{
				Gate.msgSuccess(i, r);
			}
		}
	}

	@Override
	public void sendResponseError(String r)
	{
		for(Player i : P.onlinePlayers())
		{
			if(Permissable.ACCESS.has(i))
			{
				Gate.msgError(i, r);
			}
		}
	}

	@Override
	public void sendResponseActing(String r)
	{
		for(Player i : P.onlinePlayers())
		{
			if(Permissable.ACCESS.has(i))
			{
				Gate.msgActing(i, r);
			}
		}
	}
}
