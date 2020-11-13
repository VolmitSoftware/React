package com.volmit.react.api;

import com.volmit.react.Lang;

public class EmptyActionSource implements IActionSource
{
	@Override
	public void sendResponse(String r)
	{

	}

	@Override
	public void sendResponseSuccess(String r)
	{

	}

	@Override
	public void sendResponseError(String r)
	{

	}

	@Override
	public void sendResponseActing(String r)
	{

	}

	@Override
	public String toString()
	{
		return Lang.getString("actionsource.ghost"); //$NON-NLS-1$
	}
}
