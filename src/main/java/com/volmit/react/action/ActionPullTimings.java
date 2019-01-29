package com.volmit.react.action;

import com.volmit.react.Gate;
import com.volmit.react.api.Action;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.SelectorTime;
import com.volmit.react.util.AccessCallback;
import com.volmit.react.util.C;
import com.volmit.react.util.Callback;
import com.volmit.react.util.F;

public class ActionPullTimings extends Action
{
	public ActionPullTimings()
	{
		super(ActionType.TIMINGS);
		setNodes(new String[] {"timings", "time", "times", "tr"});

		setDefaultSelector(Long.class, new AccessCallback<ISelector>()
		{
			@Override
			public ISelector get()
			{
				SelectorTime sel = new SelectorTime();
				sel.set((long) 3000);

				return sel;
			}
		});
	}

	@Override
	public void enact(IActionSource source, ISelector... selectors)
	{
		long timeFor = 3000;
		for(ISelector i : selectors)
		{
			if(i.getType().equals(Long.class))
			{
				timeFor = ((SelectorTime) i).get();
			}
		}

		source.sendResponseActing("Pulling Timings for " + F.time(timeFor, 1));

		Gate.pullTimingsReport(timeFor, new Callback<String>()
		{
			@Override
			public void run(String t)
			{
				source.sendResponseSuccess("Timings Captured " + C.WHITE + t);
				completeAction();
			}
		});
	}

	@Override
	public String getNode()
	{
		return "timings-report";
	}
}
