package com.volmit.react.util;

public abstract class A implements Runnable
{
	public A()
	{
		RQ.run(this);
	}
}
