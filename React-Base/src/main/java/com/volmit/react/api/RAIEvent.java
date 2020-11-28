package com.volmit.react.api;

public class RAIEvent
{
	private RAIEventType type;
	private String[] pars;
	private String ovt;

	public RAIEvent(RAIEventType type, String... pars)
	{
		this.pars = pars;
		this.type = type;
		ovt = type.formatFor(type.pickRandom(), pars);
	}

	@Override
	public String toString()
	{
		return ovt;
	}

	public RAIEventType getType()
	{
		return type;
	}

	public String[] getPars()
	{
		return pars;
	}

	public String getOvt()
	{
		return ovt;
	}
}
