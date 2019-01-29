package com.volmit.react.action;

import com.volmit.react.api.Action;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.CPUBenchmark;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.SelectorTime;
import com.volmit.react.util.AccessCallback;
import com.volmit.react.util.F;
import com.volmit.react.util.Task;

public class ActionCPUScore extends Action
{
	public ActionCPUScore()
	{
		super(ActionType.CPU_SCORE);
		setNodes(new String[] {"cpu-score", "cs", "cpu", "cscore", "benchmarkcpu"});

		setDefaultSelector(Long.class, new AccessCallback<ISelector>()
		{
			@Override
			public ISelector get()
			{
				SelectorTime sel = new SelectorTime();
				sel.set((long) 5000);

				return sel;
			}
		});
	}

	@Override
	public void enact(IActionSource source, ISelector... selectors)
	{
		for(Thread i : Thread.getAllStackTraces().keySet())
		{
			if(i.getClass().equals(CPUBenchmark.class))
			{
				source.sendResponseError("There is already a cpu benchmark running!");
				completeAction();
				return;
			}
		}

		long timeFor = 5000;
		for(ISelector i : selectors)
		{
			if(i.getType().equals(Long.class))
			{
				timeFor = ((SelectorTime) i).get();
			}
		}

		new CPUBenchmark(source, timeFor).start();

		source.sendResponseActing("Benchmarking CPU for " + F.time(timeFor, 1));

		new Task("", 25)
		{
			@Override
			public void run()
			{
				for(Thread i : Thread.getAllStackTraces().keySet())
				{
					if(i.getClass().equals(CPUBenchmark.class))
					{
						return;
					}
				}

				completeAction();
			}
		};
	}

	@Override
	public String getNode()
	{
		return "cpu-score";
	}
}
