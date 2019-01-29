package com.volmit.react.action;

import com.volmit.react.Info;
import com.volmit.react.Lang;
import com.volmit.react.api.Action;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.util.F;
import com.volmit.react.util.S;

public class ActionCollectGarbage extends Action
{
	public ActionCollectGarbage()
	{
		super(ActionType.COLLECT_GARBAGE);

		setNodes(Info.ACTION_COLLECT_GARBAGE_TAGS);
	}

	@Override
	public void enact(IActionSource source, ISelector... selectors)
	{
		source.sendResponseActing(Lang.getString("react.action.collect-garbagecollecting-garbage")); //$NON-NLS-1$

		long mbmem = Runtime.getRuntime().freeMemory();
		System.gc();
		long mbnex = Runtime.getRuntime().freeMemory();

		new S("action.response.gc")
		{
			@Override
			public void run()
			{
				long freed = mbnex - mbmem;

				if(freed > 0)
				{
					source.sendResponseSuccess(Lang.getString("react.action.collect-garbagecollected") + F.memSize(freed) + Lang.getString("react.action.collect-garbageof-garbage")); //$NON-NLS-1$ //$NON-NLS-2$
				}

				else
				{
					source.sendResponseError(Lang.getString("react.action.collect-garbageno-free")); //$NON-NLS-1$
				}

				completeAction();
			}
		};
	}

	@Override
	public String getNode()
	{
		return "gc";
	}
}
