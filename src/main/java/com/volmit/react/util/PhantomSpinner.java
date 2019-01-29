package com.volmit.react.util;

/**
 * Colored circle spinner
 *
 * @author cyberpwn
 */
public class PhantomSpinner
{
	private ProgressSpinner s;
	private ProgressSpinner c;

	public PhantomSpinner(C c1, C c2, C c3)
	{
		s = new ProgressSpinner();
		c = new ProgressSpinner(c1.toString(), c1.toString(), c1.toString(), c2.toString(), c3.toString(), c3.toString(), c3.toString(), c2.toString());
	}

	@Override
	public String toString()
	{
		return c.toString() + s.toString();
	}
}
