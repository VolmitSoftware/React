package com.volmit.react.util;

public abstract class S extends Execution
{
	public static ParallelPoolManager mgr;

	public S(String s)
	{
		Execution e = new Execution()
		{
			@Override
			public void run()
			{
				I.a("sync." + s, 20);
				S.this.run();
				I.b("sync." + s);
			}
		};
		e.idv = s;
		mgr.syncQueue(e);
	}
}
