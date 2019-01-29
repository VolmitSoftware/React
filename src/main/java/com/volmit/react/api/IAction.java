package com.volmit.react.api;

import com.volmit.react.util.AccessCallback;
import com.volmit.volume.lang.collections.GMap;

public interface IAction
{
	public String getName();

	public String getDescription();

	public ActionHandle getHandleType();

	public void act(IActionSource source, ISelector... selectors) throws ActionAlreadyRunningException;

	public void enact(IActionSource source, ISelector... selectors);

	public ActionState getState();

	public ActionTargetType getTarget();

	public void setNodes(String... nodes);

	public String[] getNodes();

	public String getStatus();

	public void setStatus(String status);

	public double getProgress();

	public void setProgress(double progress);

	public ActionType getType();

	public GMap<Class<?>, AccessCallback<ISelector>> getDefaultSelectors();

	public void setDefaultSelector(Class<?> clazz, AccessCallback<ISelector> selector);

	public ISelector[] biselect(ISelector... selectors);

	public IActionSource getCurrentSource();

	public void completeAction();
}
