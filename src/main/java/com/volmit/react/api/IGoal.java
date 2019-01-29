package com.volmit.react.api;

import com.volmit.volume.lang.collections.GList;

public interface IGoal
{
	public GList<IGoal> getSubgoals();

	public void propigate();

	public void onPropigated();

	public String getTag();

	public GList<IGoal> getFailingSubgoals();

	public boolean isFailing();

	public boolean onCheckFailing();

	public void update();

	public void addGoal(IGoal subgoal);
}
