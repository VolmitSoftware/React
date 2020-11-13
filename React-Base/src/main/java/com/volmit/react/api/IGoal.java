package com.volmit.react.api;

import primal.lang.collection.GList;

public interface IGoal
{
	GList<IGoal> getSubgoals();

	void propigate();

	void onPropigated();

	String getTag();

	GList<IGoal> getFailingSubgoals();

	boolean isFailing();

	boolean onCheckFailing();

	void update();

	void addGoal(IGoal subgoal);
}
