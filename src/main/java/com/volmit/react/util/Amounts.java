package com.volmit.react.util;

import org.apache.commons.lang.StringUtils;

public enum Amounts
{
	SINGLE(1),
	DUAL(2),
	TRI(3),
	QUAD(4),
	HEX(6),
	OCTA(8);

	private int a;

	private Amounts(int a)
	{
		this.a = a;
	}

	public int a()
	{
		return a;
	}

	@SuppressWarnings("deprecation")
	public static String to(int a)
	{
		for(Amounts i : Amounts.values())
		{
			if(i.a() == a)
			{
				return StringUtils.capitalise(i.toString().toLowerCase());
			}
		}

		return String.valueOf(a);
	}
}
