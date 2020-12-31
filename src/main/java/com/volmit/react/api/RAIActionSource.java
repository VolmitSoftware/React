package com.volmit.react.api;

import com.volmit.react.util.D;

public class RAIActionSource implements IActionSource
{
	@Override
	public void sendResponse(String r)
	{
		D.v("[RAI]: " + r);
	}

	@Override
	public void sendResponseSuccess(String r)
	{
		D.v("[RAI]: " + r);
	}

	@Override
	public void sendResponseError(String r)
	{
		D.v("[RAI]: " + r);
	}

	@Override
	public void sendResponseActing(String r)
	{
		D.v("[RAI]: " + r);
	}

	@Override
	public String toString()
	{
		return "RAI";
	}
}
