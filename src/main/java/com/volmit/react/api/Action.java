package com.volmit.react.api;

import org.bukkit.Chunk;

import com.volmit.react.Config;
import com.volmit.react.Lang;
import com.volmit.react.util.AccessCallback;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public abstract class Action implements IAction
{
	private String name;
	private String description;
	private String status;
	private String[] nodes;
	private ActionHandle handle;
	private ActionState state;
	private ActionTargetType target;
	private ActionType type;
	private IActionSource currentSource;
	private double progress;
	private boolean forceful;
	private GMap<Class<?>, AccessCallback<ISelector>> defaultSelectors;

	public Action(ActionType type)
	{
		this(type.getName(), type.getDescription(), type.getHandle(), type.getTarget(), type);
	}

	public Action(String name, String description, ActionHandle handle, ActionTargetType target, ActionType type)
	{
		this.name = name;
		this.description = description;
		this.handle = handle;
		this.status = ""; //$NON-NLS-1$
		this.progress = 0;
		this.state = ActionState.IDLE;
		this.target = target;
		this.type = type;
		defaultSelectors = new GMap<Class<?>, AccessCallback<ISelector>>();
		currentSource = null;
		nodes = new String[0];
		forceful = false;
	}

	public boolean isForceful()
	{
		return forceful;
	}

	public void setForceful(boolean forceful)
	{
		this.forceful = forceful;
	}

	public abstract String getNode();

	@Override
	public ISelector[] biselect(ISelector... selectors)
	{
		GList<ISelector> set = new GList<ISelector>(selectors);

		checking: for(Class<?> i : getDefaultSelectors().k())
		{
			for(ISelector j : selectors)
			{
				if(j.getType().equals(i))
				{
					continue checking;
				}
			}

			set.add(getDefaultSelectors().get(i).get());
		}

		return set.toArray(new ISelector[set.size()]);
	}

	@Override
	public void setDefaultSelector(Class<?> clazz, AccessCallback<ISelector> selector)
	{
		defaultSelectors.put(clazz, selector);
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public String getDescription()
	{
		return description;
	}

	@Override
	public ActionHandle getHandleType()
	{
		return handle;
	}

	@Override
	public ActionState getState()
	{
		return state;
	}

	@Override
	public String getStatus()
	{
		return status;
	}

	@Override
	public void setStatus(String status)
	{
		this.status = status;
	}

	@Override
	public double getProgress()
	{
		return progress;
	}

	@Override
	public void setProgress(double progress)
	{
		this.progress = progress;
	}

	@Override
	public ActionTargetType getTarget()
	{
		return target;
	}

	@Override
	public ActionType getType()
	{
		return type;
	}

	@Override
	public GMap<Class<?>, AccessCallback<ISelector>> getDefaultSelectors()
	{
		return defaultSelectors;
	}

	@Override
	public void act(IActionSource source, ISelector... selectors) throws ActionAlreadyRunningException
	{
		if(getState().equals(ActionState.RUNNING))
		{
			throw new ActionAlreadyRunningException();
		}

		state = ActionState.RUNNING;
		currentSource = source;

		int d = 0;

		for(ISelector i : biselect(selectors))
		{
			if(i.getType().equals(Chunk.class))
			{
				SelectorPosition sel = (SelectorPosition) i;

				for(Object j : new GList<Object>(sel.getPossibilities()))
				{
					Chunk cc = (Chunk) j;

					if(!Config.getWorldConfig(cc.getWorld()).allowActions)
					{
						d++;
						sel.getPossibilities().remove(cc);
					}
				}

				if(i.getPossibilities().isEmpty())
				{
					source.sendResponseError(Lang.getString("react.action.no-chunks-failed")); //$NON-NLS-1$
					completeAction();
					return;
				}
			}
		}

		if(d > 0)
		{
			source.sendResponseActing(Lang.getString("react.action.removed") + d + Lang.getString("react.action.chunks-from-selection-blocked")); //$NON-NLS-1$ //$NON-NLS-2$
		}

		enact(source, biselect(selectors));
		setForceful(false);
	}

	@Override
	public void completeAction()
	{
		state = ActionState.IDLE;
	}

	@Override
	public IActionSource getCurrentSource()
	{
		return currentSource;
	}

	@Override
	public String[] getNodes()
	{
		return nodes;
	}

	@Override
	public void setNodes(String... nodes)
	{
		this.nodes = nodes;
	}

	@Override
	public abstract void enact(IActionSource source, ISelector... selectors);
}
