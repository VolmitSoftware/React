package com.volmit.react.api;

import com.volmit.react.Lang;
import com.volmit.react.Surge;

public enum CPUResult
{
	ULTRA_SLOW(Lang.getString("react.bench.score-type.ultra-slow")), // Under 100 //$NON-NLS-1$
	VERY_SLOW(Lang.getString("react.bench.score-type.very-slow")), // Over 100 //$NON-NLS-1$
	SLOW(Lang.getString("react.bench.score-type.slow")), // Over 800 //$NON-NLS-1$
	AVERAGE(Lang.getString("react.bench.score-type.avg")), // Over 1100 //$NON-NLS-1$
	GOOD(Lang.getString("react.bench.score-type.good")), // Over 1350 //$NON-NLS-1$
	FAST(Lang.getString("react.bench.score-type.fast")), // Over 1500 //$NON-NLS-1$
	VERY_FAST(Lang.getString("react.bench.score-type.very-fast")), // Over 1700 //$NON-NLS-1$
	ULTRA_FAST(Lang.getString("react.bench.score-type.pcmr")); // Over 2000! //$NON-NLS-1$

	private String m;

	private CPUResult(String m)
	{
		this.m = m;
	}

	@Override
	public String toString()
	{
		return m;
	}

	public static String c(int s)
	{
		int mod = Surge.isObfuscated() ? 100 : 0;

		if(s + mod > 2000)
		{
			return ULTRA_FAST.toString();
		}

		if(s + mod > 1700)
		{
			return VERY_FAST.toString();
		}

		if(s + mod > 1500)
		{
			return FAST.toString();
		}

		if(s + mod > 1350)
		{
			return GOOD.toString();
		}

		if(s + mod > 1100)
		{
			return AVERAGE.toString();
		}

		if(s + mod > 800)
		{
			return SLOW.toString();
		}

		if(s + mod > 100)
		{
			return VERY_SLOW.toString();
		}

		return ULTRA_SLOW.toString();
	}
}
