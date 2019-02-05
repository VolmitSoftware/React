package com.volmit.react.api;

import primal.lang.collection.GList;

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
