package com.volmit.react.api;

import com.volmit.react.util.S;
import com.volmit.volume.lang.collections.GList;

public abstract class Goal implements IGoal
{
	private GList<IGoal> subgoals;
	private String tag;

	public Goal(String tag)
	{
		subgoals = new GList<IGoal>();
		this.tag = tag;
	}

	@Override
	public GList<IGoal> getSubgoals()
	{
		return subgoals;
	}

	@Override
	public void propigate()
	{
		if(!isFailing())
		{
			return;
		}

		for(IGoal i : getSubgoals())
		{
			new S("goal-prop")
			{
				@Override
				public void run()
				{
					i.propigate();
				}
			};
		}

		onPropigated();
	}

	@Override
	public void addGoal(IGoal subgoal)
	{
		getSubgoals().add(subgoal);
	}

	@Override
	public GList<IGoal> getFailingSubgoals()
	{
		GList<IGoal> failing = getSubgoals().copy();

		for(IGoal i : getSubgoals())
		{
			if(!i.isFailing())
			{
				failing.remove(i);
			}
		}

		return failing;
	}

	@Override
	public boolean isFailing()
	{
		return onCheckFailing();
	}

	@Override
	public String getTag()
	{
		return tag;
	}

	@Override
	public void update()
	{
		for(IGoal i : getSubgoals())
		{
			i.update();
		}

		if(isFailing())
		{
			propigate();
		}
	}

	@Override
	public abstract boolean onCheckFailing();

	@Override
	public abstract void onPropigated();
}
