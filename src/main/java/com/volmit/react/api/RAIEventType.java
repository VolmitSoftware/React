package com.volmit.react.api;

import com.volmit.react.Lang;

public enum RAIEventType
{
	FIRE_ACTION(Lang.getString("rai.event-message.react1"), Lang.getString("rai.event-message.react2"), Lang.getString("rai.event-message.react3")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	NOTE_GOAL_FAILING(Lang.getString("rai.event-message.sample1"), Lang.getString("rai.event-message.sample2"), Lang.getString("rai.event-message.sample3")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	NOTE_GOAL_FIXED(Lang.getString("rai.event-message.fixed1"), Lang.getString("rai.event-message.fixed2")); //$NON-NLS-1$ //$NON-NLS-2$

	private String[] msgs;

	private RAIEventType(String... ss)
	{
		msgs = ss;
	}

	public int getSize()
	{
		return msgs.length;
	}

	public int pickRandom()
	{
		return (int) (Math.random() * (getSize() - 1));
	}

	public String formatFor(int f, String[] pars)
	{
		String sel = msgs[f];

		int l = 0;

		for(String i : pars)
		{
			l++;

			sel = sel.replaceAll("%" + l, i); //$NON-NLS-1$
		}

		return sel;
	}
}
