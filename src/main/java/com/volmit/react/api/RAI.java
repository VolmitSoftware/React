package com.volmit.react.api;

import java.util.Arrays;

import com.volmit.react.Config;
import com.volmit.react.util.Ex;
import com.volmit.react.util.M;
import com.volmit.react.xrai.GoalManager;
import com.volmit.volume.lang.collections.GList;

public class RAI implements IRAI
{
	private GoalManager gm;
	private GList<RAIEvent> events;
	private GList<RAIEvent> logEvents;
	private GList<IActionSource> listeners;
	public long since;

	public static RAI instance;

	public RAI()
	{
		gm = new GoalManager();
		gm.createDefaultGoals();
		gm.loadGoals();
		events = new GList<RAIEvent>();
		logEvents = new GList<RAIEvent>();
		listeners = new GList<IActionSource>();
		instance = this;
		getListeners().add(new RAIActionSource());
		since = M.ms();
	}

	@Override
	public void tick()
	{
		if(!Config.RAI)
		{
			return;
		}

		gm.tick();

		try
		{
			for(RAIEvent i : logEvents.copy())
			{
				for(IActionSource j : getListeners())
				{
					j.sendResponseActing(i.toString());
				}

				if(RAIEventType.FIRE_ACTION.equals(i.getType()))
				{
					Note.RAI.bake(i.getOvt());
				}
			}
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		logEvents.clear();
	}

	@Override
	public GList<RAIEvent> getEvents()
	{
		return events;
	}

	@Override
	public void callEvent(RAIEvent e)
	{
		if(events.size() > 1 && (events.get(events.size() - 1).getType().equals(e.getType()) && Arrays.equals(events.get(events.size() - 1).getPars(), e.getPars())))
		{
			return;
		}

		events.add(e);
		logEvents.add(e);

		while(events.size() > 20)
		{
			events.remove(0);
		}

		while(logEvents.size() > 20)
		{
			logEvents.remove(0);
		}
	}

	@Override
	public GList<IActionSource> getListeners()
	{
		return listeners;
	}

	@Override
	public GoalManager getGoalManager()
	{
		return gm;
	}
}
