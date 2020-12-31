package com.volmit.react.util;

public class ProtocolRange
{
	private Protocol from;
	private Protocol to;

	public ProtocolRange(Protocol from, Protocol to)
	{
		this.from = from;
		this.to = to;
	}

	public Protocol getFrom()
	{
		return from;
	}

	public Protocol getTo()
	{
		return to;
	}

	public boolean contains(Protocol p)
	{
		return p.getCVersion() >= from.getCVersion() && p.getCVersion() <= to.getCVersion();
	}

	@Override
	public String toString()
	{
		return from.getVersionString() + " - " + to.getVersionString();
	}
}
